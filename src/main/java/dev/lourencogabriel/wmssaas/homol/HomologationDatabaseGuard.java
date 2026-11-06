package dev.lourencogabriel.wmssaas.homol;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Locale;

/** Refuse synthetic migrations before any DDL/data change in a production catalog. */
@Component
@Profile("homol")
public class HomologationDatabaseGuard implements Callback {
    static void requireIsolatedCatalog(String catalog) {
        String name = catalog == null ? "" : catalog.toLowerCase(Locale.ROOT);
        if (!name.equals("wms_saas_homol") && !name.startsWith("wms_acceptance_")) {
            throw new IllegalStateException("Homologation requires wms_saas_homol or a wms_acceptance_* database");
        }
    }

    @Override public boolean supports(Event event, Context context) { return event == Event.BEFORE_MIGRATE; }
    @Override public boolean canHandleInTransaction(Event event, Context context) { return false; }
    @Override public String getCallbackName() { return "guard-homologation-catalog"; }
    @Override public void handle(Event event, Context context) {
        try { requireIsolatedCatalog(context.getConnection().getCatalog()); }
        catch (SQLException error) { throw new IllegalStateException("Cannot verify homologation catalog", error); }
    }
}
