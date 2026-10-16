<#ftl output_format="HTML" auto_esc=true strip_whitespace=true>
<#import "brand.ftlh" as brand>
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
<body style="margin:0; padding:0; background-color:${brand.paper}; color:${brand.text}; font-family:'Manrope', Arial, Helvetica, sans-serif;">
<div style="display:none; max-height:0; overflow:hidden; opacity:0; color:transparent; line-height:1px; mso-hide:all;">
    ${preheader}
</div>
<table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" bgcolor="${brand.paper}"
       style="width:100%; border-collapse:collapse; background-color:${brand.paper};">
    <tr>
        <td align="center" style="padding:24px 12px;">
            <!--[if mso]><table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0"><tr><td><![endif]-->
            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"
                   style="width:100%; max-width:600px; border-collapse:separate; background-color:${brand.surface}; border:1px solid ${brand.border}; border-radius:12px;">
                <tr>
                    <td bgcolor="${brand.ink}" style="padding:24px; background-color:${brand.ink}; border-bottom:4px solid ${brand.accent}; border-radius:11px 11px 0 0;">
                        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="width:100%; border-collapse:collapse;">
                            <tr>
                                <td width="60" valign="middle" style="width:60px; padding-right:12px;">
                                    <img src="cid:wms-brand-icon" width="48" height="48" alt="" style="display:block; width:48px; height:48px; border:0;">
                                </td>
                                <td valign="middle" style="font-family:'Manrope', Arial, Helvetica, sans-serif;">
                                    <p style="margin:0 0 4px; color:${brand.inverse}; font-size:24px; line-height:1.3; font-weight:700;">${brand.name}</p>
                                    <p style="margin:0; color:${brand.accent}; font-size:14px; line-height:1.5;">${brand.descriptor}</p>
                                </td>
                            </tr>
                        </table>
                        <p style="margin:18px 0 0; color:${brand.inverseMuted}; font-size:14px; line-height:1.5;">${brand.tagline}</p>
                    </td>
                </tr>
                <tr>
                    <td style="padding:28px 24px; font-family:'Manrope', Arial, Helvetica, sans-serif; font-size:16px; line-height:1.5; color:${brand.text}; word-wrap:break-word; overflow-wrap:anywhere;">
                        <h1 style="margin:0 0 24px; color:${brand.text}; font-size:26px; line-height:1.25; font-weight:700;">${heading}</h1>
                        <#nested>
                    </td>
                </tr>
                <tr>
                    <td bgcolor="${brand.paper}" style="padding:20px 24px; border-top:1px solid ${brand.border}; background-color:${brand.paper}; font-family:'Manrope', Arial, Helvetica, sans-serif; border-radius:0 0 11px 11px;">
                        <p style="margin:0 0 20px; color:${brand.muted}; font-size:14px; line-height:1.6;">Esta é uma mensagem automática do ${brand.name}. Por favor, não responda a este e-mail.</p>
                        <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="border-collapse:collapse;">
                            <tr>
                                <td width="42" valign="middle" style="width:42px; padding-right:10px;">
                                    <img src="cid:wms-author-monogram" width="32" height="32" alt="" style="display:block; width:32px; height:32px; border:0;">
                                </td>
                                <td valign="middle" style="font-family:'Manrope', Arial, Helvetica, sans-serif; font-size:14px; line-height:1.5;">
                                    <p style="margin:0; color:${brand.muted};">${brand.authorLabel}</p>
                                    <a href="${brand.portfolio}" style="color:${brand.primary}; font-weight:700; text-decoration:underline;">${brand.authorName}</a>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
            <!--[if mso]></td></tr></table><![endif]-->
        </td>
    </tr>
</table>
</body>
</html>
</#macro>
