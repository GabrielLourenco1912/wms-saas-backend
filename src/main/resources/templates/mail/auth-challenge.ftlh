<#ftl output_format="HTML" auto_esc=true strip_whitespace=true>
<#import "layout.ftlh" as layout>
<#import "brand.ftlh" as brand>
<@layout.email title=subject preheader=preheader heading=heading>
    <p style="margin:0 0 18px; color:${brand.text}; font-size:16px; line-height:1.6;">
        Olá, <strong>${recipientName}</strong>!
    </p>
    <p style="margin:0 0 24px; color:${brand.text}; font-size:16px; line-height:1.6;">
        Use o código abaixo para ${action}:
    </p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"
           style="width:100%; margin:0 0 24px; border-collapse:separate;">
        <tr>
            <td align="center" bgcolor="${brand.soft}" style="padding:20px 12px; border:1px solid ${brand.border}; background-color:${brand.soft}; border-radius:8px;">
                <span style="display:inline-block; white-space:nowrap; color:${brand.primary}; font-family:'DM Mono', Consolas, 'Courier New', monospace; font-size:32px; line-height:1.5; font-weight:500; letter-spacing:4px;">${code}</span>
            </td>
        </tr>
    </table>
    <p style="margin:0 0 20px; color:${brand.muted}; font-size:14px; line-height:1.6;">
        O código expira em <strong>${expirationMinutes?c} minutos</strong> e pode ser usado uma única vez.
    </p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"
           style="width:100%; border-collapse:separate;">
        <tr>
            <td bgcolor="${brand.warningBackground}" style="padding:14px 16px; border-left:4px solid ${brand.warning}; background-color:${brand.warningBackground}; color:${brand.warning}; font-family:'Manrope', Arial, Helvetica, sans-serif; font-size:14px; line-height:1.6;">
                Se você não solicitou este código, ignore esta mensagem. Nunca compartilhe o código com outras pessoas.
            </td>
        </tr>
    </table>
</@layout.email>
