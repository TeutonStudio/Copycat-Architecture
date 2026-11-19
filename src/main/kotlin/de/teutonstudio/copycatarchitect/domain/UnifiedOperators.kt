package de.teutonstudio.copycatarchitect.domain

/** Shared catalog contract: the browser reads the very same operation signatures. */
internal object UnifiedOperators {
    private fun checked(v:Long):Int { require(v in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Integer overflow" };return v.toInt() }
    private fun add(a:Int,b:Int)=checked(a.toLong()+b)
    private fun subtract(a:Int,b:Int)=checked(a.toLong()-b)
    private fun multiply(a:Int,b:Int)=checked(a.toLong()*b)
    private fun negate(a:Int)=checked(-a.toLong())
    val kinds=setOf("COPYCAT_OPERATOR","SEGMENT_OPERATOR","POSITION_OPERATOR","NUMBER_OPERATOR","RELATION_OPERATOR","STATEMENT_OPERATOR")
    val valueKinds=kinds-setOf("COPYCAT_OPERATOR","SEGMENT_OPERATOR")
    class Family(val label:String,val description:String,val output:String,val default:String,val operations:Map<String,Operation>)
    class Operation(val label:String,val description:String,val types:List<String>,val min:Int,val max:Int,val optional:List<Int>)
    fun family(kind:String)=GraphModels.operators.getValue(kind)
    fun spec(kind:String,operation:String?)=family(kind).operations[operation ?: family(kind).default] ?: error("Unknown $kind operation: $operation")
    fun types(kind:String,operation:String?,count:Int):List<String> {
        val spec=spec(kind,operation)
        require(count in spec.min..spec.max) { "Invalid $kind input count" }
        return List(count) { i -> spec.types[minOf(i,spec.types.lastIndex)].let { if(it=="geometry") family(kind).output else it } }
    }
    fun shapeInputs(n:GraphNode):GraphModels.ShapeInputs {
        if(n.kind.name !in kinds) return checkNotNull(GraphModels.shape(n.kind).inputs)
        val op=spec(n.kind.name,n.operation)
        return GraphModels.ShapeInputs(op.min,op.max,op.types.map { if(it=="geometry") family(n.kind.name).output else it })
    }
    fun acceptedKinds(type:String):Set<BuildingKind> = BuildingKind.entries.filter { SemanticPorts.type(buildingGraphType(it))==type }.toSet()
    fun ports(n:BuildingNode):List<BuildingPort> = types(n.kind.name,n.operation,n.inputs.size).mapIndexed { i,t -> BuildingPort("in:$i",n.inputs[i],acceptedKinds(t),i) }
    fun legacyKind(operation:String)=when(operation) {
        "ROTATE"->NodeKind.ROTATE;"MIRROR"->NodeKind.MIRROR;"TRANSLATE"->NodeKind.TRANSLATE
        "INTERSECTION"->NodeKind.INTERSECTION;"DIFFERENCE"->NodeKind.DIFFERENCE;"SYMMETRIC_DIFFERENCE"->NodeKind.SYMMETRIC_DIFFERENCE
        else->NodeKind.UNION
    }
    fun switch(n:GraphNode,operation:String,nodes:Map<String,GraphNode>):GraphNode {
        val kind=if(n.kind.name in kinds) n.kind else if(n.kind==NodeKind.POSITION) NodeKind.POSITION_OPERATOR else NodeKind.COPYCAT_OPERATOR
        val spec=spec(kind.name,operation)
        val old=n.inputs.withIndex().filter { it.value.isNotEmpty() }.toMutableList()
        val count=if(spec.max>spec.min) old.count { GraphModels.shape(nodes.getValue(it.value).kind).output==family(kind.name).output }.coerceIn(spec.min,spec.max) else spec.min
        val template=n.copy(kind=kind,operation=operation,inputs=List(count) { "" })
        val groups=mutableListOf<String>()
        val inputs=types(kind.name,operation,count).map { type ->
            val found=old.firstOrNull { GraphModels.shape(nodes.getValue(it.value).kind).output==type }
            old.remove(found);groups+=found?.let { n.inputGroups.getOrNull(it.index).orEmpty() }.orEmpty();found?.value.orEmpty()
        }
        return template.copy(inputs=inputs,inputGroups=if(n.inputGroups.isEmpty()) emptyList() else groups)
    }
    fun switch(n:BuildingNode,operation:String,nodes:Map<String,BuildingNode>):BuildingNode {
        val spec=spec(n.kind.name,operation)
        val old=ports(n).filter { it.source.isNotEmpty() }.toMutableList()
        val count=if(spec.max>spec.min) old.count { SemanticPorts.type(buildingGraphType(nodes.getValue(it.source).kind))==family(n.kind.name).output }.coerceIn(spec.min,spec.max) else spec.min
        val refs=types(n.kind.name,operation,count).map { type ->
            val p=old.firstOrNull { SemanticPorts.type(buildingGraphType(nodes.getValue(it.source).kind))==type }
            old.remove(p);p?.source.orEmpty()
        }
        return n.copy(operation=operation,inputs=refs)
    }
    data class ValueNode(val id:String,val kind:String,val operation:String?,val inputs:List<String>,val number:Int,val comparison:Int,val boolean:Boolean,val position:P,val axis:RotationAxis,val turns:Int,val mirror:CopycatMirror?,val rotations:List<SegmentRotation>?=null,val mirrors:List<CopycatMirror>?=null)
    fun valueNodes(nodes:List<GraphNode>)=nodes.map { ValueNode(it.id,it.kind.name,it.operation,it.inputs,it.numberValue ?: it.countValue ?: 0,it.comparisonValue ?: 0,it.booleanValue ?: false,it.translation ?: P(),it.rotationAxis,it.quarterTurns,it.mirror) }
    fun buildingValueNodes(nodes:List<BuildingNode>)=nodes.map { ValueNode(it.id,it.kind.name,it.operation,it.inputs,it.numberValue ?: it.countValue ?: 0,it.comparisonValue ?: 0,it.booleanValue ?: false,it.translation ?: P(),it.rotationAxis,it.quarterTurns,it.mirror,it.rotations,it.mirrors) }
    fun value(nodes:List<ValueNode>,id:String):Any {
        val byId=nodes.associateBy { it.id };val active=mutableSetOf<String>();val memo=mutableMapOf<String,Any>()
        fun evaluate(key:String):Any=memo.getOrPut(key) {
            require(active.add(key)) { "Cycle in operator values" }
            val n=byId[key] ?: error("Missing operator input")
            fun arg(slot:Int)=evaluate(n.inputs.getOrNull(slot)?.takeIf(String::isNotEmpty) ?: error("Connect required value input"))
            fun number(slot:Int)=arg(slot) as? Int ?: error("Number input required")
            fun boolean(slot:Int)=arg(slot) as? Boolean ?: error("Boolean input required")
            fun position(slot:Int)=arg(slot) as? P ?: error("Position input required")
            val op=n.operation ?: if(n.kind in kinds) family(n.kind).default else "VALUE"
            val result:Any=when(n.kind) {
                "NUMBER_OPERATOR"->if(op=="VALUE") n.number else {
                    val a=number(0);val b=if(op=="ABS") 0 else number(1)
                    when(op) {
                        "ADD"->add(a,b);"SUBTRACT"->subtract(a,b);"MULTIPLY"->multiply(a,b)
                        "DIVIDE"->{ require(b!=0) { "Division by zero" };require(a!=Int.MIN_VALUE || b != -1) { "Integer overflow" };a/b }
                        "REMAINDER"->{ require(b!=0) { "Division by zero" };a%b }
                        "MIN"->minOf(a,b);"MAX"->maxOf(a,b);"ABS"->{ require(a!=Int.MIN_VALUE) { "Integer overflow" };kotlin.math.abs(a) }
                        else->error("Unknown number operation")
                    }
                }
                "RELATION_OPERATOR"->{ val a=number(0);val b=if(n.inputs.getOrNull(1).isNullOrEmpty()) n.comparison else number(1)
                    when(op) { "EQUAL"->a==b;"NOT_EQUAL"->a!=b;"LESS"->a<b;"LESS_EQUAL"->a<=b;"GREATER"->a>b;"GREATER_EQUAL"->a>=b;"DIVISIBLE"->{ require(b!=0) { "Divisibility by zero is undefined" };a%b==0 };else->error("Unknown relation") }
                }
                "STATEMENT_OPERATOR"->if(op=="VALUE") n.boolean else {
                    val a=boolean(0);val b=if(op=="NOT") false else boolean(1)
                    when(op) { "AND"->a && b;"OR"->a || b;"XOR"->a xor b;"NOT"->!a;"IMPLIES"->!a || b;"EQUIVALENT"->a==b;else->error("Unknown statement") }
                }
                "INT"->n.number
                "POSITION_X","POSITION_Y","POSITION_Z"->{val p=position(0);when(n.kind) { "POSITION_X"->p.x;"POSITION_Y"->p.y;else->p.z }}
                "INT_ADD","INT_SUBTRACT","INT_MULTIPLY","INT_DIVIDE","INT_MIN","INT_MAX"->{val a=number(0);val b=number(1);when(n.kind.removePrefix("INT_")) { "ADD"->add(a,b);"SUBTRACT"->subtract(a,b);"MULTIPLY"->multiply(a,b);"DIVIDE"->{require(b!=0) { "Division by zero" };checked(a.toLong()/b)};"MIN"->minOf(a,b);else->maxOf(a,b) }}
                "POSITION","POSITION_OPERATOR"->if(op=="VALUE") {
                    fun component(i:Int,fallback:Int)=if(n.inputs.getOrNull(i).isNullOrEmpty()) fallback else number(i)
                    P(component(0,n.position.x),component(1,n.position.y),component(2,n.position.z))
                } else {
                    val a=position(0)
                    when(op) {
                        "ADD","SUBTRACT","MULTIPLY","DIVIDE","MIN","MAX"->{ val b=position(1)
                            fun f(x:Int,y:Int)=when(op) { "ADD"->add(x,y);"SUBTRACT"->subtract(x,y);"MULTIPLY"->multiply(x,y);"DIVIDE"->{require(y!=0);checked(x.toLong()/y)};"MIN"->minOf(x,y);else->maxOf(x,y) }
                            P(f(a.x,b.x),f(a.y,b.y),f(a.z,b.z)) }
                        "SCALE"->{ val b=number(1);P(multiply(a.x,b),multiply(a.y,b),multiply(a.z,b)) }
                        "ROTATE","MIRROR"->{
                            val parameter=n.inputs.getOrNull(1)?.takeIf(String::isNotEmpty)?.let { byId.getValue(it) } ?: n
                            if(op=="ROTATE") (parameter.rotations ?: listOf(SegmentRotation(parameter.axis,parameter.turns))).fold(a) { current,step -> rotate(current,step.axis,step.quarterTurns) }
                            else (parameter.mirrors ?: listOf(parameter.mirror ?: CopycatMirror())).fold(a) { current,step -> when(step.plane) {
                                CopycatMirrorPlane.X->current.copy(x=negate(current.x));CopycatMirrorPlane.Y->current.copy(y=negate(current.y));CopycatMirrorPlane.Z->current.copy(z=negate(current.z))
                                else->error("Position mirroring requires plane X, Y or Z")
                            } }
                        }
                        else->error("Unknown position operation")
                    }
                }
                else->{
                    require(n.kind.startsWith("POSITION_")) { "Node does not provide a scalar value" }
                    val a=position(0);val b=position(1)
                    fun component(x:Int,y:Int):Int=when(n.kind.removePrefix("POSITION_")) { "ADD"->add(x,y);"SUBTRACT"->subtract(x,y);"MULTIPLY"->multiply(x,y);"DIVIDE"->{require(y!=0) { "Division by zero" };checked(x.toLong()/y)};"MIN"->minOf(x,y);"MAX"->maxOf(x,y);else->error("Unknown position operation") }
                    P(component(a.x,b.x),component(a.y,b.y),component(a.z,b.z))
                }
            }
            active.remove(key);result
        }
        return evaluate(id)
    }
    fun rotate(p:P,axis:RotationAxis,turns:Int):P {
        var v=p
        repeat(turns) { v=when(axis) { RotationAxis.X->P(v.x,negate(v.z),v.y);RotationAxis.Z->P(negate(v.y),v.x,v.z);else->P(negate(v.z),v.y,v.x) } }
        return v
    }
}
