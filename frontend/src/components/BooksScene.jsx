import { Component, Suspense, useEffect, useRef, useState } from 'react'
import { Canvas, useFrame } from '@react-three/fiber'
import { Float, RoundedBox, ContactShadows } from '@react-three/drei'
import { useReducedMotion } from 'framer-motion'
import { BookOpen } from 'lucide-react'

function Box({ size, position, color, ...rest }) {
  return <mesh position={position} {...rest}><boxGeometry args={size} /><meshStandardMaterial color={color} roughness={.48} /></mesh>
}
function Book({ color, position, rotation, scale = 1, reduced }) {
  return <Float enabled={!reduced} speed={1.4} rotationIntensity={.16} floatIntensity={.4}><group position={position} rotation={rotation} scale={scale}>
    <RoundedBox args={[1.65, 2.18, .38]} radius={.055} smoothness={3}><meshStandardMaterial color={color} roughness={.38} metalness={.08} /></RoundedBox>
    <Box size={[1.48, 2.04, .24]} position={[.07, 0, 0]} color="#fff9e9" />
    <Box size={[1.65, 2.18, .055]} position={[0, 0, .18]} color={color} />
    <Box size={[.07, 2.18, .012]} position={[-.65, 0, .218]} color="#ffffff" />
    <Box size={[.68, .075, .012]} position={[.07, .62, .218]} color="#ffffff" />
    <Box size={[.42, .035, .012]} position={[.07, .46, .218]} color="#c5f5ed" />
    <mesh position={[.07, -.08, .23]} rotation={[0, 0, Math.PI / 4]}><torusGeometry args={[.28, .025, 8, 4]} /><meshStandardMaterial color="#d9f99d" metalness={.3} roughness={.3} /></mesh>
    <Box size={[.42, .03, .012]} position={[.07, -.65, .218]} color="#ffffff" />
    {[...Array(7)].map((_, i) => <Box key={i} size={[1.47, .008, .008]} position={[.08, 1.024, -.09 + i * .029]} color="#d7d1c0" />)}
    <Box size={[.18, .4, .015]} position={[.39, -1.13, .08]} color="#d9f99d" />
  </group></Float>
}
function OpenBook({ reduced }) {
  return <Float enabled={!reduced} speed={1.1} floatIntensity={.35} rotationIntensity={.12}><group position={[.05, -1.42, 1.15]} rotation={[.48, -.2, -.08]} scale={.85}>
    {[-1, 1].map(side => <group key={side} rotation={[0, side * -.22, 0]}>
      <Box size={[1.38, 1.6, .09]} position={[side * .7, 0, 0]} color="#0e7490" />
      <Box size={[1.3, 1.5, .1]} position={[side * .7, 0, .07]} color="#fff9eb" />
      {[...Array(7)].map((_, i) => <Box key={i} size={[i === 0 ? .68 : 1, .015, .005]} position={[side * .7, .48 - i * .14, .126]} color={i === 0 ? '#0891b2' : '#c1c4c0'} />)}
    </group>)}
  </group></Float>
}
function Scene({ reduced }) {
  const group = useRef()
  useFrame(({ pointer }, dt) => { if (!reduced && group.current) { group.current.rotation.y += (pointer.x * .12 - group.current.rotation.y) * Math.min(1, dt * 3); group.current.rotation.x += (-pointer.y * .08 - group.current.rotation.x) * Math.min(1, dt * 3) } })
  return <group ref={group}>
    <Book color="#0e7490" position={[.65, .35, .2]} rotation={[-.13, -.4, -.2]} scale={1.2} reduced={reduced} />
    <Book color="#7c3aed" position={[-1.2, .03, -.3]} rotation={[.18, .35, .25]} scale={.85} reduced={reduced} />
    <Book color="#f59e0b" position={[-1.35, 1.63, -.9]} rotation={[-.2, .5, -.23]} scale={.46} reduced={reduced} />
    <OpenBook reduced={reduced} />
    <Float enabled={!reduced} speed={1.8} floatIntensity={.5}><group position={[1.9, 1.6, 0]} rotation={[.2, .2, -.18]} scale={.65}>
      <mesh><cylinderGeometry args={[.48, .4, .35, 24]} /><meshStandardMaterial color="#163a4a" /></mesh>
      <Box size={[1.45, .1, 1.2]} position={[0, .23, 0]} color="#164e63" />
      <Box size={[.04, .48, .04]} position={[.57, 0, .25]} color="#fbbf24" />
      <mesh position={[.57, -.29, .25]}><sphereGeometry args={[.07, 12, 12]} /><meshStandardMaterial color="#fbbf24" /></mesh>
    </group></Float>
    <Float enabled={!reduced} speed={1.7}><group position={[-2.15, -.65, .5]} rotation={[.1, 0, -.35]}>
      <mesh><cylinderGeometry args={[.075, .075, 1.45, 6]} /><meshStandardMaterial color="#fbbf24" /></mesh>
      <mesh position={[0, -.86, 0]} rotation={[0, 0, Math.PI]}><coneGeometry args={[.075, .25, 6]} /><meshStandardMaterial color="#e8c8a0" /></mesh>
      <mesh position={[0, .77, 0]}><cylinderGeometry args={[.077, .077, .16, 12]} /><meshStandardMaterial color="#fb7185" /></mesh>
    </group></Float>
    {[[-2, .6, 1], [1.8, -.9, .2], [.1, 2.15, -.2]].map((pos, i) => <mesh key={i} position={pos}><sphereGeometry args={[.07 + i * .02, 16, 16]} /><meshStandardMaterial color={['#a3e635', '#38bdf8', '#fbbf24'][i]} metalness={.2} roughness={.2} /></mesh>)}
  </group>
}
function Fallback() { return <div className="css-library"><div className="css-book book-purple"><div className="css-book-cover"><span>اكتشف</span><BookOpen size={50} strokeWidth={1} /><small>كل يوم، فكرة جديدة</small></div></div><div className="css-book book-teal"><div className="css-book-cover"><span>المعرفة</span><BookOpen size={64} strokeWidth={1} /><small>من هنا يبدأ مستقبلك</small></div></div><div className="css-book book-amber"><div className="css-book-cover"><span>تعلّم</span><BookOpen size={28} /></div></div><div className="css-open-book"><div /><div /></div><div className="library-shadow" /></div> }
class SceneBoundary extends Component {
  state = { failed: false }
  static getDerivedStateFromError() { return { failed: true } }
  render() { return this.state.failed ? <Fallback /> : this.props.children }
}
export default function BooksScene({ className = '' }) {
  const ref = useRef(null)
  const reduced = useReducedMotion()
  const [visible, setVisible] = useState(true)
  const [webgl, setWebgl] = useState(false)
  useEffect(() => { try { const canvas = document.createElement('canvas'); const context = canvas.getContext('webgl2') || canvas.getContext('webgl'); if (context) { context.getExtension('WEBGL_lose_context')?.loseContext(); setWebgl(true) } } catch { /* The CSS book scene stays available without a GPU. */ } }, [])
  useEffect(() => { const io = new IntersectionObserver(([e]) => setVisible(e.isIntersecting), { threshold: .01 }); io.observe(ref.current); return () => io.disconnect() }, [])
  return <div ref={ref} className={className} aria-hidden="true">{webgl ? <SceneBoundary><Canvas dpr={[1, 1.5]} camera={{ position: [0, .1, 8.6], fov: 43 }} gl={{ antialias: true, alpha: true }} frameloop={reduced || !visible ? 'demand' : 'always'} fallback={<Fallback />}>
    <ambientLight intensity={1.3} /><directionalLight position={[3, 5, 6]} intensity={3} color="#fff4da" /><directionalLight position={[-4, 1, 2]} intensity={1.8} color="#b9f5ff" />
    <Suspense fallback={null}><Scene reduced={reduced} /><ContactShadows position={[0, -2.4, 0]} opacity={.25} scale={10} blur={2.7} far={4} frames={1} /></Suspense>
  </Canvas></SceneBoundary> : <Fallback />}</div>
}
