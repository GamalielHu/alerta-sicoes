import re, json, os, requests
from bs4 import BeautifulSoup
B="https://www.sicoes.gob.bo"
os.makedirs("out2",exist_ok=True)
def save(n,t): open("out2/"+n,"w",encoding="utf-8").write(t)
s=requests.Session(); s.headers["User-Agent"]="Mozilla/5.0 (Linux; Android 14) Chrome/129 Mobile"
def tok_idx():
    return BeautifulSoup(s.get(B+"/portal/index.php").text,"html.parser").select_one("input#token")["value"]
t=tok_idx()
convurl=B+"/portal/contrataciones/busqueda/convocatorias.php?tipo=convNacional&token="+t
conv=s.get(convurl,headers={"Referer":B+"/portal/index.php"}).text
soup=BeautifulSoup(conv,"html.parser")
tokS=soup.select_one("form#formSimple input[name=token]")["value"]
tokId=soup.select_one("input#token")["value"]
cols=json.loads(soup.select_one("#colsConvocatoriasSimple")["value"])
d=dict(token=tokS,entidad="",objetoContrato="",publicacionDesde="",publicacionHasta="",presentacionPropuestasDesde="",presentacionPropuestasHasta="",r1="11",subasta="",obras="O",tipo="Simple",operacion="convNacional",nroRegistros="10",draw="1",start="0",length="10",captcha="")
for i in range(1,7): d["cuce%d"%i]=""
j=s.post(B+"/portal/contrataciones/operacion.php",data=d,headers={"X-Requested-With":"XMLHttpRequest","Referer":convurl}).json()
cuce=j["data"][0][cols[0]]
log=[]
for nombre,tk in [("tokId",tokId),("tokS",tokS)]:
    r=s.get(B+"/portal/contrataciones/ficha/fichaProceso.php?cp="+cuce+"&token="+tk,headers={"Referer":convurl})
    log.append(f"{nombre} -> {r.url} {len(r.text)}")
    save("ficha_"+nombre+".html",r.text)
t2=tok_idx()
r=s.get(B+"/portal/contrataciones/ficha/fichaProceso.php?cp="+cuce+"&token="+t2,headers={"Referer":B+"/portal/index.php"})
log.append(f"idx -> {r.url} {len(r.text)}"); save("ficha_idx.html",r.text)
# busqueda por CUCE
p=cuce.split("-")
d2=dict(d); d2.update(token=tokS)
for i,v in enumerate(p,1): d2["cuce%d"%i]=v
d2["obras"]="O"
try:
    jj=s.post(B+"/portal/contrataciones/operacion.php",data=d2,headers={"X-Requested-With":"XMLHttpRequest","Referer":convurl}).text
    log.append("por cuce: "+jj[:300])
except Exception as e: log.append("por cuce ERR "+str(e))
save("log.txt","\n".join(log))
