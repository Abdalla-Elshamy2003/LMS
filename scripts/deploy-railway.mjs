import { spawnSync } from 'node:child_process'
import { setTimeout } from 'node:timers/promises'

const project = '73e0e9ea-06ed-49bf-b9a0-fb1d20d65d66'
const environment = '7a5a430d-eb7f-4069-b2f2-7b47f314e29f'
const services = [
  ['backend', 'a425938a-3969-4bf9-b17c-3161ea29cdc6'],
  ['frontend', '1c6a7b45-3e49-4f92-940a-7c80b53e7294'],
]

function railway(args, json = false) {
  const result = spawnSync('railway', args, { encoding: 'utf8', timeout: 300_000, stdio: json ? 'pipe' : 'inherit' })
  if (result.error || result.status !== 0) throw new Error(`Railway command failed: ${args[0]}`)
  return json ? JSON.parse(result.stdout) : undefined
}

async function main() {
  if (!process.env.RAILWAY_TOKEN) throw new Error('Configure RAILWAY_DROOS_DEPLOY_TOKEN in repository Actions secrets')
  for (const [name, id] of services) {
    const selectors = ['--project', project, '--environment', environment, '--service', id]
    const list = () => railway(['deployment', 'list', ...selectors, '--json'], true)
    const previous = new Set(list().map(d => d.id))
    railway(['up', name, '--path-as-root', ...selectors, '--detach', '--message', `GitHub ${process.env.RELEASE_SHA}`])
    const deadline = Date.now() + 12 * 60_000
    let deploymentId
    let succeeded = false
    while (Date.now() < deadline) {
      const deployments = list()
      deploymentId ??= deployments.find(d => !previous.has(d.id))?.id
      const deployment = deployments.find(d => d.id === deploymentId)
      if (deployment) {
        console.log(`${name}: ${deployment.id} ${deployment.status}`)
        if (deployment.status === 'SUCCESS') { succeeded = true; break }
        if (['FAILED', 'CRASHED', 'REMOVED', 'SKIPPED'].includes(deployment.status)) throw new Error(`${name} deployment failed`)
      }
      await setTimeout(15_000)
    }
    if (!succeeded) throw new Error(`${name} did not become healthy before the deadline`)
  }
  for (const path of ['/healthz', '/api/public/academies/mr-mohammed-alazmi']) {
    const response = await fetch(`https://droos.com.co${path}`, {
      headers: { 'User-Agent': 'Mozilla/5.0 (compatible; DroosCatalogVerifier/1.0)' },
      signal: AbortSignal.timeout(30_000),
    })
    if (!response.ok) throw new Error(`Production smoke check failed: ${path} (${response.status})`)
    if (path.startsWith('/api/')) {
      const page = await response.json()
      if (!page.profile.published || page.profile.demoContent || page.courses.length !== 9) throw new Error('Production catalog verification failed')
    }
  }
  console.log('Production deployment and public catalog checks passed')
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
