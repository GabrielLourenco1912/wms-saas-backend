package dev.lourencogabriel.wmssaas.partner.service;

import dev.lourencogabriel.wmssaas.partner.model.Partner;
import dev.lourencogabriel.wmssaas.partner.repository.PartnerRepository;
import dev.lourencogabriel.wmssaas.partner.dto.PartnerDtos;
import dev.lourencogabriel.wmssaas.web.exception.BusinessRuleException;
import dev.lourencogabriel.wmssaas.web.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class PartnerService {
    private final PartnerRepository partners;
    private final EntityManager em;

    public Page<Partner> partners(Pageable p) { return partners.findAll(page(p)); }

    @Transactional
    public Partner partner(String id,PartnerDtos.PartnerRequest r) {
        Partner e=id==null?new Partner():lock(Partner.class,id);String tax=normalized(r.taxId());
        if(tax!=null)require(partners.findAll().stream().noneMatch(x->!Objects.equals(x.getId(),id)&&tax.equals(x.getTaxId())),"Partner tax ID is already used");
        e.setLegalName(r.legalName().trim());e.setTaxId(tax);e.setType(r.type());e.setActive(r.active());return partners.saveAndFlush(e);
    }

    public <T> T get(Class<T> type,String id) {
        T found=em.find(type,id);
        if(found==null)throw new ResourceNotFoundException(type.getSimpleName(),id);
        return found;
    }

    private <T> T lock(Class<T> type,String id) {
        T found=em.find(type,id,LockModeType.PESSIMISTIC_WRITE);
        if(found==null)throw new ResourceNotFoundException(type.getSimpleName(),id);
        return found;
    }

    private static Pageable page(Pageable p) {
        return PageRequest.of(Math.max(0,p.getPageNumber()),Math.min(200,p.getPageSize()),Sort.by("id"));
    }

    private static void require(boolean ok,String message) { if(!ok)throw new BusinessRuleException(message); }

    private static String normalized(String s) { return s==null||s.isBlank()?null:s.trim(); }

    private void active(Boolean active,String what) { require(Boolean.TRUE.equals(active),what+" must be active"); }
}
