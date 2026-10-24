"""Check local HTML links/assets against files and annotated servlet routes."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote
import json
import re

root = Path(__file__).resolve().parents[1]
folders = ['Admin/web', 'Landlord', 'HomePage', 'UserDashboard', 'createAccount']
routes = set()
for folder in ['Admin/src', 'Landlord/java-servlets', 'HomePage/java-servlets', 'UserDashboard/java-servlets', 'createAccount/java-servlets']:
    for source in (root / folder).rglob('*.java'):
        text = source.read_text(encoding='utf-8-sig')
        for annotation in re.findall(r'@WebServlet\((.*?)\)', text, re.S):
            routes.update(re.findall(r'"(/[^" ]+)"', annotation))

class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key in ('src', 'href', 'action') and value:
                self.links.append((tag, value))

issues = []
pages = sorted({p for folder in folders for p in (root / folder).rglob('*.html')}) + [root / 'index.html']
for page in pages:
    parser = Links()
    parser.feed(page.read_text(encoding='utf-8-sig'))
    for tag, value in parser.links:
        url = urlsplit(value)
        if url.scheme or url.netloc or not url.path:
            continue
        target = (root / unquote(url.path).lstrip('/')) if value.startswith('/') else (page.parent / unquote(url.path))
        target = target.resolve()
        try:
            relative = '/' + target.relative_to(root).as_posix()
        except ValueError:
            issues.append({'page': str(page.relative_to(root)), 'link': value, 'reason': 'outside application'})
            continue
        if not target.exists() and relative not in routes and not any(r.endswith('/*') and relative.startswith(r[:-1]) for r in routes):
            issues.append({'page': str(page.relative_to(root)), 'link': value, 'reason': 'missing local target'})
print(json.dumps({'pages_checked': len(pages), 'issues': issues}, indent=2))
