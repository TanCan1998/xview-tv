#!/usr/bin/env python3
"""Select verification from a Git diff; unknown bases/config changes stay conservative."""
import json, os, subprocess, sys
from pathlib import Path

JVM = ['core/model','core/domain','core/provider','core/data','core/network','provider/demo']
DEPS = {'core/model': [], 'core/domain': ['core/model'], 'core/provider': ['core/model'],
        'core/data': ['core/domain','core/provider'], 'core/network': ['core/domain'],
        'provider/demo': ['core/provider'], 'core/designsystem': [],
        'feature/home': ['core/domain','core/designsystem'],
        'app': ['feature/home','core/domain','core/network','core/designsystem']}

def select(paths, full=False):
    config = any(p.startswith(('gradle/','build-logic/','.github/workflows/','tools/scripts/')) or
                 p in ['gradlew','gradlew.bat','gradle.properties','settings.gradle.kts','build.gradle.kts'] or
                 p.endswith('/build.gradle.kts') for p in paths)
    full = full or config
    changed = {m for m in DEPS if any(p.startswith(m+'/') for p in paths)}
    affected = set(changed)
    while True:
        more = {m for m, deps in DEPS.items() if any(d in affected for d in deps)}
        if more <= affected: break
        affected |= more
    pairing = full or any(p.startswith('web/pairing/') for p in paths)
    android = full or 'app' in affected or any(p.startswith('web/pairing/') and '/tests/' not in p and not p.endswith('.md') for p in paths)
    tasks = [':'+m.replace('/',':')+':test' for m in JVM if full or m in affected]
    if android: tasks += [':app:lintOptimized', ':app:assembleOptimized']
    structure = full or bool(changed) or pairing
    return {'android':android,'pairing':pairing,'structure':structure,'gradle':bool(tasks),'tasks':' '.join(tasks),'full':full}

def run(*a): return subprocess.run(a,capture_output=True,text=True)

def main():
    event=json.loads(Path(os.environ['GITHUB_EVENT_PATH']).read_text())
    kind=os.environ['GITHUB_EVENT_NAME']; full=kind=='workflow_dispatch'
    base=event.get('pull_request',{}).get('base',{}).get('sha') if kind=='pull_request' else event.get('before')
    paths=[]
    if not base or set(base)=={'0'}: full=True
    elif not full:
        if run('git','cat-file','-e',base+'^{commit}').returncode:
            fetch=run('git','fetch','--no-tags','--depth=1','origin',base)
            if fetch.returncode:full=True
        if not full:
            diff=run('git','diff','--name-only',base,'HEAD')
            if diff.returncode:full=True
            else:paths=diff.stdout.splitlines()
    plan=select(paths,full)
    with open(os.environ['GITHUB_OUTPUT'],'a') as f:
        for k,v in plan.items():
            value = str(v).lower() if isinstance(v, bool) else str(v)
            f.write(k+'='+value+'\n')
    print('Verification plan:',json.dumps(plan))
    print('Changed paths:',len(paths))
    summary=os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary,'a') as f:f.write('## Affected verification\n\n```json\n'+json.dumps(plan,indent=2)+'\n```\n')

if __name__=='__main__':main()
