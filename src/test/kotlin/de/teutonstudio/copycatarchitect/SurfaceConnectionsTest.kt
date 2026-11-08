package de.teutonstudio.copycatarchitect

import de.teutonstudio.copycatarchitect.domain.SurfaceConnections
import de.teutonstudio.copycatarchitect.domain.TextureSurface
import kotlin.test.*

class SurfaceConnectionsTest {
    private data class Surface(override val vertices:List<FloatArray>,override val normal:FloatArray=floatArrayOf(0f,1f,0f)):TextureSurface
    private fun top(y:Float=1f,x:Float=0f,z:Float=0f,X:Float=1f,Z:Float=1f)=Surface(listOf(floatArrayOf(x,y,z),floatArrayOf(X,y,z),floatArrayOf(X,y,Z),floatArrayOf(x,y,Z)))
    @Test fun `seams need equal planes and a real shared edge`() {
        val offset=floatArrayOf(1f,0f,0f)
        assertTrue(SurfaceConnections.coplanar(top(),top(),offset))
        assertTrue(SurfaceConnections.touch(top(),top(),offset))
        assertFalse(SurfaceConnections.coplanar(top(0.5f),top(),offset))
        assertFalse(SurfaceConnections.touch(top(),top(x=0.125f),offset))
        assertFalse(SurfaceConnections.touch(top(Z=0.5f),top(z=0.5f),offset))
        assertTrue(SurfaceConnections.touch(top(Z=0.75f),top(z=0.5f),offset))
    }
    @Test fun `diagonal contexts require the actual common corner`() {
        val offset=floatArrayOf(1f,0f,1f)
        assertTrue(SurfaceConnections.touch(top(),top(),offset))
        assertFalse(SurfaceConnections.touch(top(Z=0.9f),top(),offset))
        assertFalse(SurfaceConnections.touch(top(),top(x=0.1f),offset))
    }
    @Test fun `exact sloping triangles join across their actual plane`() {
        val normal=floatArrayOf(0f,0.70710677f,-0.70710677f)
        val a=Surface(listOf(floatArrayOf(0f,0f,0f),floatArrayOf(1f,0f,0f),floatArrayOf(1f,1f,1f),floatArrayOf(1f,1f,1f)),normal)
        val b=Surface(listOf(floatArrayOf(0f,0f,0f),floatArrayOf(0f,1f,1f),floatArrayOf(1f,1f,1f),floatArrayOf(1f,1f,1f)),normal)
        val offset=floatArrayOf(1f,0f,0f)
        assertTrue(SurfaceConnections.coplanar(a,b,offset))
        assertTrue(SurfaceConnections.touch(a,b,offset))
        assertFalse(SurfaceConnections.coplanar(a,b,floatArrayOf(0f,1f,0f)))
        assertFalse(SurfaceConnections.coplanar(a,b.copy(normal=floatArrayOf(0f,1f,0f)),offset))
    }
}
