# Identidade visual dos e-mails

Os e-mails transacionais do WMS SaaS usam a mesma identidade do web e do mobile: **Precisão em movimento**, a frase “Cada movimento, sob controle.”, W de percurso e crédito de Gabriel Lourenço com o Monograma GL / 01.

O cabeçalho apresenta **WMS SaaS — Gestão de armazéns**. A identificação anterior “Portal acadêmico” foi removida por não descrever o produto.

## Fluxos e organização

Confirmação de e-mail, autenticação em duas etapas e recuperação de senha compartilham `templates/mail/auth-challenge.ftlh` e `layout.ftlh`. `brand.ftlh` fornece os tokens versionados. Os títulos e as ações específicas continuam vindo de `AuthChallengeService`, assim como o código e a duração efetivamente configurada.

A mudança de apresentação não altera destinatários, geração/consumo de códigos, limites de tentativa, duração, tokens, endpoints ou regras de autenticação. A alternativa de texto simples ganhou identificação e crédito, mantendo o código isolado em sua linha, inclusive zeros à esquerda.

## Renderização e transporte

- Layout em tabelas de apresentação, largura fluida com máximo de 600 px e estilos essenciais inline. Existe um contêiner condicional de 600 px para Outlook com mecanismo MSO.
- Floresta `#14251E` no cabeçalho, lima `#D1F878` na marca, papel `#F5F6F0` no fundo e verde `#176B48` no código e no link de autoria. O aviso de segurança usa os papéis de atenção da identidade.
- Manrope e DM Mono aparecem primeiro nas famílias CSS. Clientes sem essas fontes usam Arial/Helvetica e Consolas/Courier, respectivamente. Não há fontes web externas ou pressuposição de suporte a fontes personalizadas em e-mail.
- `SmtpEmailSender` mantém UTF-8 e as alternativas `text/plain` e `text/html`. Imagens são adicionadas depois dos corpos como partes inline de `multipart/related`, identificadas por CID.
- `EmailBrandAssets` permite somente dois recursos de classpath conhecidos: ícone do produto e monograma do autor. Não busca imagens por URL. O produto tem nome/descritor em texto e a autoria tem nome textual; imagens são decorativas e sua ausência não remove informações necessárias.
- FreeMarker mantém `auto_esc=true`. Nomes e demais campos são escapados antes da inserção no HTML. A sanitização existente de assunto e a validação de endereços permanecem ativas.

Os PNGs estão em `src/main/resources/mail/branding/`; `provenance.json` registra origem e SHA-256. A assinatura mantém os bytes do arquivo fornecido pelo autor. Nenhuma fonte é distribuída pelo backend.

## Atualização e prévias locais

Os recursos da identidade já estão criados e versionados. Para ajustar a marca,
edite `templates/mail/brand.ftlh`, as constantes em `EmailBranding.java` e,
se necessário, as imagens em `mail/branding/`, atualizando a procedência e os
hashes em `provenance.json`. Valide a renderização e o transporte:

```bash
./mvnw -Dtest=EmailTemplateRendererTest,SmtpEmailSenderTest,AuthChallengeServiceTest test
```

O JAR utiliza os recursos versionados e não depende de um script de geração,
da pasta local de documentação ou do Python em produção.

Para produzir os três HTMLs de demonstração sem enviar e-mail:

```bash
./mvnw -Dtest=EmailTemplateRendererTest \
  -Dwms.email.preview-directory=../docs-gerais/identidade-visual/emails test
```

As prévias usam pessoa fictícia, código `001234` e prazo demonstrativo de dez minutos. Somente nesses arquivos de inspeção os CIDs são substituídos por imagens incorporadas em data URI. O envio real continua usando CID e o prazo vem da configuração do desafio.

## Validação e limites

`./mvnw verify` foi aprovado em 16/10/2026 com **452 testes, sem falhas ou testes ignorados**, incluindo MySQL/Testcontainers. Os testes de e-mail cobrem os três propósitos, zeros à esquerda, prazo no conteúdo, escape HTML, alternativa textual, composição MIME e igualdade dos bytes das imagens incorporadas.

As prévias renderizadas também foram conferidas em Chromium, em 320, 390, 760 e 1440 px, sem overflow e sem recursos HTTP externos, incluindo leitura com imagens removidas. Nenhum e-mail foi enviado a terceiros. Inspeção em Gmail, Outlook, Apple Mail e aparelhos reais permanece uma validação de distribuição, porque os motores de renderização podem aplicar regras próprias de fontes, imagens e modo escuro.
