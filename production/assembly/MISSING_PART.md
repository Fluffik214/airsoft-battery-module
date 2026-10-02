# Part missing from the LCSC / JLCPCB BOM

`bom_lcsc.csv` and `bom_jlcpcb.csv` have 40 lines. The board needs 41. This is the one that's missing:

| Designator | Part | Manufacturer | Package | Qty per board |
|---|---|---|---|---|
| **U5** | **AP7381-33SA-7** (3.3 V LDO regulator, 40 V input) | Diodes Incorporated | SOT-23 | 1 |

## Why it's missing

The LCSC listing (C3752764) was discontinued, so the part was taken off the LCSC BOM.

## Where to buy it

Mouser, DigiKey, TME or Farnell. Search for `AP7381-33SA-7`.

## Warning

**Do NOT substitute** another 3.3 V SOT-23 regulator (TLV760, LM3480, HT7533, ME6203 and similar).
Their pin order is different, so they would put USB voltage on the 3.3 V rail and damage the MCU and BMS.
