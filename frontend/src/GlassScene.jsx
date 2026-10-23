
import { Canvas } from "@react-three/fiber";
import { Float, RoundedBox, Environment } from "@react-three/drei";
import { Suspense } from "react";

function GlassPanel({ position, rotation, size, color }) {
  return (
    <Float speed={1.1} floatIntensity={0.22} rotationIntensity={0.08}>
      <group position={position} rotation={rotation}>
        <RoundedBox args={size} radius={0.12} smoothness={4}>
          <meshPhysicalMaterial
            color={color}
            metalness={0.65}
            roughness={0.2}
            transmission={0.15}
            thickness={0.5}
            clearcoat={1}
            clearcoatRoughness={0.15}
            transparent
            opacity={0.92}
          />
        </RoundedBox>

        <mesh position={[0, size[1] * 0.28, size[2] / 2 + 0.008]}>
          <boxGeometry args={[size[0] * 0.55, 0.035, 0.008]} />
          <meshStandardMaterial
            color="#f5dba5"
            emissive="#b88b45"
            emissiveIntensity={0.45}
          />
        </mesh>
      </group>
    </Float>
  );
}

function Scene() {
  return (
    <>
      <ambientLight intensity={1.1} />
      <directionalLight
        position={[3, 5, 5]}
        intensity={2.5}
        color="#ffe5b0"
      />
      <pointLight position={[-4, 1, 2]} intensity={20} color="#c28b48" />
      <pointLight position={[3, -3, -2]} intensity={12} color="#f4d7a0" />

      <GlassPanel
        position={[-0.65, 0.5, 0]}
        rotation={[0.08, -0.16, -0.13]}
        size={[1.9, 1.25, 0.09]}
        color="#69492f"
      />

      <GlassPanel
        position={[0.7, -0.45, 0.45]}
        rotation={[-0.08, 0.18, 0.12]}
        size={[1.65, 1.05, 0.09]}
        color="#9c7749"
      />

      <GlassPanel
        position={[0.05, 0.1, 0.85]}
        rotation={[0.04, -0.08, -0.03]}
        size={[1.4, 0.82, 0.08]}
        color="#b08b55"
      />
    </>
  );
}

export default function GlassScene() {
  return (
    <div className="glass-scene">
      <Canvas
        camera={{ position: [0, 0, 6], fov: 42 }}
        dpr={[1, 1.5]}
        gl={{ antialias: true, alpha: true }}
      >
        <color attach="background" args={["#21160f"]} />
        <Suspense fallback={null}>
          <Scene />
          <Environment preset="studio" />
        </Suspense>
      </Canvas>
    </div>
  );
}
