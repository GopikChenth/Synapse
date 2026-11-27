# Regenerates the Material 3 color schemes in core/designsystem/Theme.kt.
# Usage: pip install materialyoucolor && python genscheme.py > schemes.kt.txt

from materialyoucolor.hct.hct import Hct
from materialyoucolor.scheme.scheme_tonal_spot import SchemeTonalSpot
from materialyoucolor.scheme.scheme_fidelity import SchemeFidelity
from materialyoucolor.dynamiccolor.dynamic_scheme import SpecVersion
from materialyoucolor.dynamiccolor.material_dynamic_colors import MaterialDynamicColors as M
from materialyoucolor.palettes.tonal_palette import TonalPalette
from materialyoucolor.blend.blend import Blend

ROLES = ["primary","onPrimary","primaryContainer","onPrimaryContainer","inversePrimary",
 "secondary","onSecondary","secondaryContainer","onSecondaryContainer",
 "tertiary","onTertiary","tertiaryContainer","onTertiaryContainer",
 "background","onBackground","surface","onSurface","surfaceVariant","onSurfaceVariant",
 "surfaceTint","inverseSurface","inverseOnSurface",
 "error","onError","errorContainer","onErrorContainer",
 "outline","outlineVariant","scrim",
 "surfaceBright","surfaceContainer","surfaceContainerHigh","surfaceContainerHighest",
 "surfaceContainerLow","surfaceContainerLowest","surfaceDim"]

THEMES = [  # (name, seed, scheme class, doc)
  ("StandardPurple", 0xFF6750A4, SchemeTonalSpot),
  ("DeepSpace",      0xFF00E5FF, SchemeFidelity),
  ("MidnightGreen",  0xFF52A435, SchemeFidelity),
  ("TacticalHUD",    0xFF24FCDE, SchemeFidelity),
]
STATUS = [("success", 0xFF10B981), ("warning", 0xFFF59E0B)]

def hexc(argb): return f"Color(0x{argb & 0xFFFFFFFF:08X})"

out = []
out.append("// GENERATED with the Material Color Utilities algorithm (spec 2021).")
out.append("// Do not hand-edit; regenerate from the seed colors listed below.\n")
for name, seed, cls in THEMES:
    for dark in (False, True):
        s = cls(Hct.from_int(seed), dark, 0.0, spec_version="2021")
        fn = "darkColorScheme" if dark else "lightColorScheme"
        out.append(f"internal val {name}{'Dark' if dark else 'Light'} = {fn}(")
        for r in ROLES:
            out.append(f"    {r} = {hexc(getattr(M, r).get_argb(s))},")
        out[-1] = out[-1].rstrip(",")
        out.append(")\n")
    # status (custom colors harmonized to the theme seed, M3 custom-color tones)
    for dark in (False, True):
        vals = []
        for sname, sseed in STATUS:
            h = Blend.harmonize(sseed, seed)
            pal = TonalPalette.from_int(h)
            if dark: t = dict(c=80, on=20, cont=30, onc=90)
            else:    t = dict(c=40, on=100, cont=90, onc=10)
            vals += [(sname, pal.tone(t['c'])), ("on"+sname.capitalize(), pal.tone(t['on'])),
                     (sname+"Container", pal.tone(t['cont'])), ("on"+sname.capitalize()+"Container", pal.tone(t['onc']))]
        out.append(f"internal val {name}Status{'Dark' if dark else 'Light'} = SynapseStatusScheme(")
        for k,v in vals: out.append(f"    {k} = {hexc(v)},")
        out[-1] = out[-1].rstrip(",")
        out.append(")\n")
print("\n".join(out))
