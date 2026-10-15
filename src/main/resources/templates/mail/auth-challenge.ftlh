<#ftl output_format="HTML" auto_esc=true strip_whitespace=true>
<#import "layout.ftlh" as layout>
<@layout.email title=subject preheader=preheader heading=heading>
    <p style="margin:0 0 18px; color:#344b4d; font-size:16px; line-height:1.7;">
        Olá, <strong>${recipientName}</strong>!
    </p>
    <p style="margin:0 0 24px; color:#344b4d; font-size:16px; line-height:1.7;">
        Use o código abaixo para ${action}:
    </p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"
           style="width:100%; margin:0 0 24px; border-collapse:separate;">
        <tr>
            <td align="center" style="padding:22px; border:1px solid #a7d0cc; background-color:#eaf3f1; border-radius:8px;">
                <span style="color:#066c6f; font-family:'Courier New', Courier, monospace; font-size:34px; font-weight:700; letter-spacing:8px;">
                    ${code}
                </span>
            </td>
        </tr>
    </table>
    <p style="margin:0 0 20px; color:#344b4d; font-size:14px; line-height:1.7;">
        O código expira em <strong>${expirationMinutes?c} minutos</strong> e pode ser usado uma única vez.
    </p>
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0"
           style="width:100%; border-collapse:separate;">
        <tr>
            <td style="padding:14px 16px; border-left:4px solid #d16b36; background-color:#fff7f2; color:#765444; font-size:13px; line-height:1.6;">
                Se você não solicitou este código, ignore esta mensagem. Nunca compartilhe o código com outras pessoas.
            </td>
        </tr>
    </table>
</@layout.email>
