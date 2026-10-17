import re, json, os, urllib.parse, requests
from bs4 import BeautifulSoup
B="https://www.sicoes.gob.bo"
os.makedirs("out",exist_ok=True)
def save(n,t):
    open("out/"+n,"w",encoding="utf-8").write(t if isinstance(t,str) else json.dumps(t,ensure_ascii=False,indent=1))
s=requests.Session(); s.headers["User-Agent"]="Mozilla/5.0 (Linux; Android 14) Chrome/129 Mobile"
idx=s.get(B+"/portal/index.php").text
t1=BeautifulSoup(idx,"html.parser").select_one("input#token")["value"]
conv=s.get(B+"/portal/contrataciones/busqueda/convocatorias.php?tipo=convNacional&token="+t1).text
save("conv.html",conv)
soup=BeautifulSoup(conv,"html.parser")
tok=soup.select_one("form#formSimple input[name=token]")["value"]
colsS=json.loads(soup.select_one("#colsConvocatoriasSimple")["value"])
colsA=json.loads(soup.select_one("#colsConvocatoriasAvanzada")["value"])
thA=[th.get_text(" ",strip=True) for th in soup.select("#tablaAvanzada thead th")]
save("cols.json",{"simple":colsS,"avanzada":colsA,"thAvanzada":thA,"thSimple":[th.get_text(" ",strip=True) for th in soup.select("#tablaSimple thead th")]})
def post(d):
    return s.post(B+"/portal/contrataciones/operacion.php",data=d,headers={"X-Requested-With":"XMLHttpRequest","Referer":B+"/portal/contrataciones/busqueda/convocatorias.php"}).text
base=dict(token=tok,entidad="",objetoContrato="",publicacionDesde="",publicacionHasta="",presentacionPropuestasDesde="",presentacionPropuestasHasta="",r1="11",subasta="",obras="O",tipo="Simple",operacion="convNacional",nroRegistros="10",draw="1",start="0",length="10",captcha="")
for i in range(1,7): base["cuce%d"%i]=""
r=post(base); save("simple.json",r)
j=json.loads(r); row=j["data"][0]
dec={k:(urllib.parse.unquote(v) if isinstance(v,str) else v) for k,v in row.items()}
save("fila_simple.json",dec)
# avanzada
adv=dict(token=tok,entidad="",codigoDpto="",objetoContrato="",codigoModalidad="",r1="11",codigoContrato="O",nroContrato="",codigoNormativa="",montoDesde="",montoHasta="",publicacionDesde="",publicacionHasta="",presentacionPropuestasDesde="",presentacionPropuestasHasta="",desiertaDesde="",desiertaHasta="",subasta="",nroDocumento="",nombreAdjudicado="",tipo="Avanzada",operacion="convNacional",nroRegistros="10",draw="1",start="0",length="10",captcha="")
for i in range(1,7): adv["cuce%d"%i]=""
for k in ["personaContDespliegue","nomtoGarDespliegue","costoPlieDespliegue","arpcDespliegue","fechaReunionDespliegue","fechaAdjudicacionDespliegue","dptoDespliegue","normativaDespliegue"]: pass
ra=post(adv); save("avanzada.json",ra)
# scripts
for js in ["/lib/js/app/portal/formularioModal.js","/lib/js/app/portal/archivos.js","/lib/js/app/portal/archivosModal.js","/lib/download/downloadserpub.min.js","/lib/js/app/comun.js","/lib/download/downloadprint.js"]:
    save(js.split("/")[-1]+".txt",s.get(B+js+"?v=0.0.3").text)
# formulario 100
m=re.search(r"verFormulario\('([^']+)'",dec.get(colsS[10],"") or "")
save("form100_link.txt",str(m and m.group(1)))
cuce=row[colsS[0]]
idx2=s.get(B+"/portal/index.php").text
t3=BeautifulSoup(idx2,"html.parser").select_one("input#token")["value"]
f=s.get(B+"/portal/contrataciones/ficha/fichaProceso.php?cp="+cuce+"&token="+t3)
save("ficha.html",f.text)
save("resumen.txt","cuce="+cuce+"\nform100="+str(m and m.group(1)))
