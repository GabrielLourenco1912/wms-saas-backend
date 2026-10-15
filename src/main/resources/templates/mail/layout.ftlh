<#ftl output_format="HTML" auto_esc=true strip_whitespace=true>
<#macro email title preheader heading>
<!doctype html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="color-scheme" content="light">
    <meta name="supported-color-schemes" content="light">
    <title>${title}</title>
</head>
<body style="margin:0; padding:0; background-color:#eef4f2; color:#203436; font-family:Arial, Helvetica, sans-serif;">
<div style="display:none; max-height:0; overflow:hidden; opacity:0; color:transparent; line-height:1px;">
    ${preheader}
</div>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" bgcolor="#eef4f2"
       style="width:100%; border-collapse:collapse; background-color:#eef4f2;">
    <tr>
        <td align="center" style="padding:32px 16px;">
            <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"
                   style="width:100%; max-width:600px; border-collapse:separate; background-color:#ffffff; border:1px solid #dbe7e4; border-radius:12px; overflow:hidden;">
                <tr>
                    <td bgcolor="#203436" style="padding:28px 36px; background-color:#203436;">
                        <p style="margin:0 0 8px; color:#79cbc7; font-size:11px; font-weight:700; letter-spacing:2px; text-transform:uppercase;">
                            Portal acadêmico
                        </p>
                        <p style="margin:0; color:#ffffff; font-size:25px; font-weight:700; letter-spacing:-0.5px;">
                            WMS <span style="color:#ef9b6d;">Gestão de Armazém</span>
                        </p>
                    </td>
                </tr>
                <tr>
                    <td style="padding:38px 36px 34px;">
                        <h1 style="margin:0 0 24px; color:#203436; font-size:28px; line-height:1.2; letter-spacing:-0.6px;">
                            ${heading}
                        </h1>
                        <#nested>
                    </td>
                </tr>
                <tr>
                    <td style="padding:22px 36px; border-top:1px solid #dbe7e4; background-color:#f8faf9;">
                        <p style="margin:0; color:#6e7574; font-size:12px; line-height:1.6;">
                            Esta é uma mensagem automática do WMS. Por favor, não responda a este e-mail.
                        </p>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</body>
</html>
</#macro>
