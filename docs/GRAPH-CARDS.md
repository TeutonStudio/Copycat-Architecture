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

Legacy explicit roofs use adaptiveRoof=true with surface, ridge, inner corner,
outer corner and wall segment inputs. Native geometry chooses slope/eave,
ridge, valley, edge/hip and wall usage. Dynamic templates receive exact slope
periods or boundary-wall dimensions. Missing sources use generated geometry.
Old direct roofs and six-variant explicit roofs keep their behavior. Area modes,
dynamic segments and adaptive roofs require schema 7; old documents without
these properties keep their schema.

The organization adapter owns React presentation and version management. Its
generated browser codec reads these native DTOs. The JVM/browser/JVM corpus
includes count-bound areas, dynamic consumers, adaptive roofs and typed cards.


## Inline authoring and conditional values

Canvas and insertion picker use the same configured node card. Operation,
transformation order, structural modes, dimensions and operand count determine
the visible sockets before insertion. Input circles on the right are drag handles
for reorderable operands; their source wires, operand metadata and named group
terminals move together. One configuration change is one undoable graph edit.
STOREY selection offers both an isolated 3D preview and a 2D room arrangement.
Room-instance offsets are staged in both views and committed together.

BOOLEAN defaults to false. INT_COMPARE includes equality, ordering and DIVISIBLE
(A/B is an integer, with zero divisor diagnosed). BOOLEAN_LOGIC offers AND, OR,
XOR and NOT. CONDITIONAL inputs are A, Boolean, B; only the selected value is
evaluated. Full structural typing and cycle detection still cover both branches.
These functions require form schema 8 or building schema 12.

## Roof template values and ordinary shaft segments

ROOF_TEMPLATE is a versionable typed result with eight optional component inputs:
straight slope, inner corner, outer corner, straight ridge, 90-degree ridge,
T ridge, X ridge and single peak. Only components required by the actual geometry
must be supplied. Templates default to a gable roof with 1:1 slope and retain roof
form, ridge direction, height mode, height, knee wall and overhang.

New ROOF nodes default to TEMPLATE mode (AREA and ROOF_TEMPLATE). DIRECT mode
exposes AREA and the same eight component sockets. Both modes share the exact
roof generator and support standalone previews and subsequent segment operators.
Switching modes preserves the common area and inactive configuration/wires.
Existing legacy roof fields and modes remain readable.

SERVICE_SHAFT_SEGMENT and ELEVATOR_SHAFT_SEGMENT are ordinary dynamic-Y segment
recipes. The building supplies heights, floors and stops; standalone evaluation
uses an explicit preview height. Transformations carry geometry and opening
regions together; Boolean operations clip openings to the remaining shaft domain.
Existing floor-bound shaft definitions retain their legacy evaluation.

## Signed integers and counts

Form schema 9 and building schema 13 distinguish INT (-2147483648..2147483647)
from COUNT (0..2147483647). COUNT widens to INT. The opposite direction requires
INT_TO_COUNT, which diagnoses negative values. INT_ABS returns COUNT and diagnoses
the unrepresentable absolute value of INT_MIN. New number sources default to
COUNT zero. Position components are signed INT; repeat and raster dimensions are
COUNT with their existing additional minimum sizes.

Arithmetic type inference depends on operand types, never on current values.
Addition, multiplication, min, max, division and remainder return COUNT only when
all operands are COUNT. Subtraction always returns INT. Open, ambiguous operands
remain editable and have unknown output type. Mixed numeric conditional branches
join to INT. Division truncates toward zero; remainder is a-(a/b)*b. Zero divisors
and integer overflow are repairable diagnostics.

Opening an old document preserves its old numeric rules. Upgrading preserves INT
sources and inserts explicit INT_TO_COUNT nodes on pre-existing connections to
new COUNT inputs, including named group inputs. Migration and its triggering edit
share the same undo step. JVM/browser/JVM exchange tests include conditional,
roof-template, shaft and signed-position/absolute-value graphs.
