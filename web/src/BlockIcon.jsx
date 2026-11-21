import { useEffect,useState } from 'react'
import { useWorkspace } from './Workspace.jsx'

// Isometric inventory-style cube. Each visible face uses its own block texture.
export function BlockIcon({id}) {
  const {catalog}=useWorkspace(),[asset,setAsset]=useState(null),[failed,setFailed]=useState(false)
  useEffect(() => {let live=true;setAsset(null);setFailed(false);catalog.resolve(id).then(data=>{if(live)setAsset(data)}).catch(()=>{if(live)setFailed(true)});return()=>{live=false}},[catalog,id])
  const faces=asset?.faces || [],fallback=faces.find(Boolean),available=Boolean(fallback) && !failed
  const image=(face,transform)=><image href={face || fallback} width="32" height="32" preserveAspectRatio="xMinYMin slice" transform={transform} onError={()=>setFailed(true)} />
  return <svg className="block-inventory-icon" viewBox="0 0 64 64" role="img" aria-label={available ? `Blockvorschau: ${id}` : `Ersatzvorschau: ${id}`}>
    <polygon points="32,3 60,17 32,31 4,17" fill="#b5b8bc"/><polygon points="4,17 32,31 32,61 4,47" fill="#83888d"/><polygon points="32,31 60,17 60,47 32,61" fill="#5f646a"/>
    {available && <>{image(faces[2],'matrix(.875 .4375 -.875 .4375 32 3)')}{image(faces[4],'matrix(.875 .4375 0 .9375 4 17)')}{image(faces[0],'matrix(.875 -.4375 0 .9375 32 31)')}
      <polygon points="4,17 32,31 32,61 4,47" fill="#000" opacity=".12"/><polygon points="32,31 60,17 60,47 32,61" fill="#000" opacity=".3"/></>}
    {!available && <text x="32" y="40" textAnchor="middle" fill="white" fontSize="24">?</text>}
  </svg>
}
