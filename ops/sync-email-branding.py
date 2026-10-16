#!/usr/bin/env python3
"""Sincronização manual. O JAR utiliza somente os recursos versionados no backend."""
import hashlib
import json
from pathlib import Path
import re
import sys

BACKEND = Path(__file__).resolve().parents[1]
SOURCE = BACKEND.parent / 'docs-gerais/identidade-visual'
CHECK = '--check' in sys.argv


def write(relative, data):
    target = BACKEND / relative
    if CHECK:
        if not target.is_file() or target.read_bytes() != data:
            raise ValueError(f'Recurso desatualizado: {relative}. Execute python3 ops/sync-email-branding.py.')
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)


try:
    tokens = json.loads((SOURCE / 'tokens.json').read_text())
    selected = json.loads((SOURCE / 'assinatura/selecao.json').read_text())
    files = []
    for origin, name in [
        ('icone/web/icon-192.png', 'wms-icon.png'),
        (tokens['assets']['authorCredit']['pngLight'], 'author-monogram.png'),
    ]:
        data = (SOURCE / origin).read_bytes()
        digest = hashlib.sha256(data).hexdigest()
        if name == 'author-monogram.png':
            assert any(item['sha256'] == digest for item in selected['files']), 'Assinatura modificada'
        write('src/main/resources/mail/branding/' + name, data)
        files.append({'file': name, 'source': origin, 'sha256': digest})
    colors = tokens['color']
    roles = {
        'ink': colors['brand']['ink'], 'primary': colors['brand']['primary'],
        'accent': colors['brand']['accent'], 'soft': colors['brand']['soft'],
        'paper': colors['background'], 'surface': colors['surface'],
        'text': colors['text']['primary'], 'muted': colors['text']['secondary'],
        'inverse': colors['text']['inverse'], 'inverseMuted': colors['text']['inverseSecondary'],
        'border': colors['border']['subtle'], 'warning': colors['semantic']['warning']['foreground'],
        'warningBackground': colors['semantic']['warning']['background'],
    }
    assert all(re.fullmatch(r'#[0-9a-fA-F]{6}', value) for value in roles.values())
    values = {**roles, 'name': tokens['metadata']['project'], 'descriptor': tokens['metadata']['descriptor'],
              'tagline': tokens['metadata']['tagline'], 'authorName': tokens['assets']['authorCredit']['name'],
              'authorLabel': tokens['assets']['authorCredit']['label'], 'portfolio': tokens['assets']['authorCredit']['portfolio']}
    template = '<#ftl output_format="HTML" auto_esc=true strip_whitespace=true>\n<#-- Gerado por ops/sync-email-branding.py. -->\n'
    for key, value in values.items():
        template += '<#assign ' + key + '=' + json.dumps(value, ensure_ascii=False) + '>\n'
    write('src/main/resources/templates/mail/brand.ftlh', template.encode())
    java = '// Gerado por ops/sync-email-branding.py.\npackage dev.lourencogabriel.wmssaas.mail;\n\npublic final class EmailBranding {\n\n'
    for constant, key in [('NAME', 'name'), ('DESCRIPTOR', 'descriptor'), ('TAGLINE', 'tagline'), ('AUTHOR_NAME', 'authorName'), ('AUTHOR_LABEL', 'authorLabel'), ('PORTFOLIO', 'portfolio')]:
        java += '    public static final String ' + constant + ' = ' + json.dumps(values[key], ensure_ascii=False) + ';\n'
    java += '\n    private EmailBranding() {\n    }\n}\n'
    write('src/main/java/dev/lourencogabriel/wmssaas/mail/EmailBranding.java', java.encode())
    write('src/main/resources/mail/branding/provenance.json', (json.dumps({'concept': tokens['metadata']['concept'], 'signatureSource': selected['source'], 'files': files}, ensure_ascii=False, indent=2) + '\n').encode())
    print('Identidade dos e-mails ' + ('conferida' if CHECK else 'sincronizada') + '.')
except (OSError, ValueError, KeyError, AssertionError) as error:
    print(f'Não foi possível sincronizar a identidade: {error}', file=sys.stderr)
    raise SystemExit(1)
