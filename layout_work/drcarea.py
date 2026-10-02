import re,sys
t=open(sys.argv[1],encoding='utf8').read()
x0,x1=float(sys.argv[2]),float(sys.argv[3])
skip=set(sys.argv[4].split(',')) if len(sys.argv)>4 else set()
n=0
for b in re.split(r'\n(?=\[)',t):
    m=re.match(r'\[(\w+)\]',b)
    if not m or m.group(1) in skip: continue
    pts=re.findall(r'@\(([\d.]+) mm, ([\d.]+) mm\)',b)
    if pts and any(x0<=float(x)<=x1 for x,y in pts):
        print(b.strip()[:330]); print('--'); n+=1
print('count',n)
