import {useEffect} from 'react'
import {useUpdateNodeInternals} from '@xyflow/react'
import {NodeAppearance} from './NodeAppearance.jsx'
export {NodeAppearance} from './NodeAppearance.jsx'
export function DiagramNode({id,data,selected}) {
 const update=useUpdateNodeInternals(),signature=JSON.stringify([data.ports,data.outputs,data.node.kind,data.node.operation,data.node.transformationOrder])
 useEffect(()=>update(id),[id,signature,update])
 return <NodeAppearance {...{data,selected}}/>
}
