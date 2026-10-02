"""Regenerate every production output from the installed schematic + PCB.
python layout_work/make_production.py      (run from anywhere; needs KiCad 10's kicad-cli)

Writes: production/gerbers/*, production/assembly/{pick-and-place.csv, bom.csv, assembly-top.pdf,
assembly-bottom.pdf}, bom.csv (copy), schematic.pdf, production/airsoft-module-rev-b-{gerbers,production}.zip.
The LCSC / JLCPCB order BOMs (bom_lcsc.csv, bom_jlcpcb.csv) hold verified LCSC numbers and are edited by hand.
"""
import csv, io, os, shutil, subprocess, zipfile

KC = r"C:/Program Files/KiCad/10.0/bin/kicad-cli.exe"
P = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PCB = os.path.join(P, "usbc pd airsoft module.kicad_pcb")
SCH = os.path.join(P, "usbc pd airsoft module.kicad_sch")
PROD = os.path.join(P, "production")
GER, ASM = os.path.join(PROD, "gerbers"), os.path.join(PROD, "assembly")
REV = "rev-b"

# notes that live only in the BOM (not in the schematic)
BOM_NOTES = {
    "R13-R16": "Cell input Rc (sets ~37 mA balance current), 1%, >=0.1 W",
    "R5,R27-R29": "R27-R29 = cell LED lines (charlieplexed)",
    "F1": "protects the balance-lead wires (charge path J2 pin 1)",
}


def kc(*args):
    subprocess.run([KC, *args], check=True, stdout=subprocess.DEVNULL)


for f in os.listdir(GER):
    os.remove(os.path.join(GER, f))
kc("pcb", "export", "gerbers", "-o", GER + os.sep,
   "--layers", "F.Cu,B.Cu,F.Paste,B.Paste,F.SilkS,B.SilkS,F.Mask,B.Mask,Edge.Cuts", PCB)
kc("pcb", "export", "drill", "-o", GER + os.sep, "--format", "excellon", "--excellon-separate-th",
   "--generate-map", "--map-format", "gerberx2", "--drill-origin", "absolute", "--excellon-units", "mm", PCB)
kc("pcb", "export", "pos", "-o", os.path.join(ASM, "pick-and-place.csv"), "--format", "csv",
   "--units", "mm", "--side", "both", PCB)
kc("pcb", "export", "pdf", "--mode-single", "--sp", "--layers", "F.Fab,F.SilkS,Edge.Cuts",
   "-o", os.path.join(ASM, "assembly-top.pdf"), PCB)
kc("pcb", "export", "pdf", "--mode-single", "--sp", "--mirror", "--layers", "B.Fab,B.SilkS,Edge.Cuts",
   "-o", os.path.join(ASM, "assembly-bottom.pdf"), PCB)
kc("sch", "export", "pdf", "-o", os.path.join(P, "schematic.pdf"), SCH)

bom = os.path.join(ASM, "bom.csv")
kc("sch", "export", "bom", "-o", bom, "--fields", "Reference,Value,Footprint,MPN,Note,${QUANTITY}",
   "--labels", "Refs,Value,Footprint,MPN,Note,Qty", "--group-by", "Value,Footprint",
   "--ref-range-delimiter", "-", "--exclude-dnp", SCH)
rows = list(csv.reader(open(bom, encoding="utf8")))
for r in rows:
    if r[0] in BOM_NOTES:
        r[4] = BOM_NOTES[r[0]]
s = io.StringIO()
csv.writer(s, quoting=csv.QUOTE_ALL, lineterminator="\n").writerows(rows)
open(bom, "w", encoding="utf8", newline="").write(s.getvalue())
shutil.copy(bom, os.path.join(P, "bom.csv"))

gz = os.path.join(PROD, f"airsoft-module-{REV}-gerbers.zip")
names = sorted(os.listdir(GER))
with zipfile.ZipFile(gz, "w", zipfile.ZIP_DEFLATED) as z:
    for f in names:
        z.write(os.path.join(GER, f), f)
with zipfile.ZipFile(os.path.join(PROD, f"airsoft-module-{REV}-production.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    for f in names:
        z.write(os.path.join(GER, f), "gerbers/" + f)
    for f in sorted(os.listdir(ASM)):
        z.write(os.path.join(ASM, f), "assembly/" + f)
    z.write(os.path.join(PROD, "ORDERING.md"), "ORDERING.md")
    z.write(gz, os.path.basename(gz))
print("production files written:", len(names), "gerber/drill files")

# ================================================================= cell LED board (led_board/)
LB = os.path.join(P, "led_board")
LPCB, LSCH = os.path.join(LB, "led_board.kicad_pcb"), os.path.join(LB, "led_board.kicad_sch")
LPROD = os.path.join(PROD, "led_board")
LGER = os.path.join(LPROD, "gerbers")
os.makedirs(LGER, exist_ok=True)
for f in os.listdir(LGER):
    os.remove(os.path.join(LGER, f))
kc("pcb", "export", "gerbers", "-o", LGER + os.sep,
   "--layers", "F.Cu,B.Cu,F.Paste,B.Paste,F.SilkS,B.SilkS,F.Mask,B.Mask,Edge.Cuts", LPCB)
kc("pcb", "export", "drill", "-o", LGER + os.sep, "--format", "excellon", "--excellon-separate-th",
   "--generate-map", "--map-format", "gerberx2", "--drill-origin", "absolute", "--excellon-units", "mm", LPCB)
kc("pcb", "export", "pos", "-o", os.path.join(LPROD, "pick-and-place.csv"), "--format", "csv",
   "--units", "mm", "--side", "both", LPCB)
kc("pcb", "export", "pdf", "--mode-single", "--sp", "--layers", "F.Fab,F.SilkS,Edge.Cuts",
   "-o", os.path.join(LPROD, "assembly-top.pdf"), LPCB)
kc("pcb", "export", "pdf", "--mode-single", "--sp", "--mirror", "--layers", "B.Fab,B.SilkS,Edge.Cuts",
   "-o", os.path.join(LPROD, "assembly-bottom.pdf"), LPCB)
kc("sch", "export", "pdf", "-o", os.path.join(LPROD, "led_board-schematic.pdf"), LSCH)
kc("sch", "export", "bom", "-o", os.path.join(LPROD, "bom.csv"), "--fields", "Reference,Value,Footprint,MPN,LCSC,${QUANTITY}",
   "--labels", "Refs,Value,Footprint,MPN,LCSC,Qty", "--group-by", "Value,Footprint",
   "--ref-range-delimiter", "-", "--exclude-dnp", LSCH)
with open(os.path.join(LPROD, "bom_lcsc.csv"), "w", encoding="utf8", newline="") as f:
    w = csv.writer(f, lineterminator="\n")
    w.writerow(["Quantity", "LCSC Part Number", "Manufacture Part Number", "Manufacturer", "Package", "Value", "Designator", "Note"])
    w.writerow([3, "C598341", "KPHBM-2012SURKCGKC", "Kingbright", "2012 (0805) 4-pad", "red / yellow-green LED", "S1,S2,S3",
                "one per cell; order spares"])
lnames = sorted(os.listdir(LGER))
lgz = os.path.join(LPROD, "led-board-rev-a-gerbers.zip")   # first revision of the LED board
with zipfile.ZipFile(lgz, "w", zipfile.ZIP_DEFLATED) as z:
    for f in lnames:
        z.write(os.path.join(LGER, f), f)
with zipfile.ZipFile(os.path.join(LPROD, "led-board-rev-a-production.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    for f in lnames:
        z.write(os.path.join(LGER, f), "gerbers/" + f)
    for f in sorted(os.listdir(LPROD)):
        fp = os.path.join(LPROD, f)
        if os.path.isfile(fp) and not f.endswith("-production.zip"):
            z.write(fp, f)
print("LED board files written:", len(lnames), "gerber/drill files")
