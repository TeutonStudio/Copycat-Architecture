# Typed graph cards, areas and dynamic segments

Graphstudio uses one typed card for all native value families. The native
representation remains a Building document (document schema 2). One BUILDING
node with non-null resultType is the terminal; inputs[0] references its source.
Other BUILDING nodes are ordinary producers. resultType uses buildingGraphType,
including COPYCAT, INT, POSITION, WALL (segment), ROOF, BUILDING, GARDEN and AREA.
Port compatibility follows the semantic family. Legacy graphs retain their
original terminal rules.

COPYCAT nodes can carry an optional inlinePart. Its native geometry, size,
material surfaces and ordered transformations remain editable. Referenced forms
retain exact library keys. Typed terminals and inline parts require schema 8.
The building plan compiler accepts BUILDING-output cards; other families use the
native value or isolated geometry evaluator.

AREA modes: SQUARE exposes one Anzahl dimension, RECTANGLE two (X/Z), DRAW none
and stores exact coordinates. Missing areaMode preserves legacy coordinate
lists. Empty drawn areas are incomplete drafts. Existing connectivity and hole
checks remain active at room consumers. Nonempty areas retain their raster
bounds and 16384-cell limit.

SEGMENT dynamicAxes X/Y/Z mark recipe parameters, not geometric stretching.
Dynamic sources use one interior Copycat input. Layout and boundary assignments
remain stored but inactive. Every consumer resolves its own dimensions; caches
include both source ID and requested dimensions. Rotation permutes axes and
size requests. Mirrors retain axes. Boolean operators propagate constraints
before geometry; union offsets reduce each operand's available extent. Ordered
patterns distribute the remaining stacking length over dynamic inputs after
fixed inputs; integer remainders follow input order. Conflicts remain diagnostics.

New explicit roofs use adaptiveRoof=true with surface, ridge, inner corner,
outer corner and wall segment inputs. Native geometry chooses slope/eave,
ridge, valley, edge/hip and wall usage. Dynamic templates receive exact slope
periods or boundary-wall dimensions. Missing sources use generated geometry.
Old direct roofs and six-variant explicit roofs keep their behavior. Area modes,
dynamic segments and adaptive roofs require schema 7; old documents without
these properties keep their schema.

The organization adapter owns React presentation and version management. Its
generated browser codec reads these native DTOs. The JVM/browser/JVM corpus
includes count-bound areas, dynamic consumers, adaptive roofs and typed cards.
