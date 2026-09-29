import sys, re, html
src, dst = sys.argv[1], sys.argv[2]
raw = open(src, encoding='utf-8', errors='ignore').read()
# isolate article body if present
m = re.search(r'<article[^>]*>(.*?)</article>', raw, re.S|re.I)
body = m.group(1) if m else raw
body = re.sub(r'<(script|style|noscript)[^>]*>.*?</\1>', ' ', body, flags=re.S|re.I)
body = re.sub(r'<br\s*/?>', '\n', body, flags=re.I)
body = re.sub(r'</(p|div|li|tr|h1|h2|h3|h4|h5|td|th)>', '\n', body, flags=re.I)
body = re.sub(r'<[^>]+>', ' ', body)
body = html.unescape(body)
lines = [re.sub(r'\s+', ' ', l).strip() for l in body.split('\n')]
lines = [l for l in lines if l]
open(dst, 'w', encoding='utf-8').write('\n'.join(lines))
print(f"{dst}: {len(lines)} lines")
