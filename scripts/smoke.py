#!/usr/bin/env python3
"""Exercise all API acceptance journeys and restart the live Compose stack."""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

base = f"http://localhost:{os.environ.get('UI_PORT', '5173')}"
def api(method, path, body=None, user='demo-author', expected=200):
    headers = {'X-Demo-User': user, 'Content-Type': 'application/json'}
    req = urllib.request.Request(base + '/api/v1' + path, headers=headers, method=method,
                                 data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    assert status == expected, (method, path, status, data.decode())
    return json.loads(data)

def healthy():
    raw = subprocess.check_output(['docker', 'compose', 'ps', '--format', 'json'], text=True).strip()
    services = json.loads(raw) if raw.startswith('[') else [json.loads(line) for line in raw.splitlines()]
    assert len(services) == 3, services
    assert all(service['Health'] == 'healthy' for service in services), services

def restart():
    subprocess.run(['docker', 'compose', 'restart'], check=True)
    deadline = time.monotonic() + 180
    while True:
        try:
            healthy()
            api('GET', '/drafts')
            return
        except (AssertionError, OSError, urllib.error.URLError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(1)

healthy()
with urllib.request.urlopen(base) as response:
    assert b'<div id="root"></div>' in response.read()
seed = api('GET', '/drafts/BILL-REFUND-001')
assert seed['revision'] >= 1
sop_id = 'SMOKE-' + uuid.uuid4().hex[:16].upper()
source = Path('backend/src/main/resources/template.md').read_text().replace('BILL-REFUND-001', sop_id)
assert api('PUT', '/drafts/' + sop_id, {'source': source})['revision'] == 1
api('GET', '/sops/' + sop_id, user='demo-consumer', expected=404)
preview = api('POST', '/validate', {'source': source})
assert preview['valid'] and not preview['issues']
v1 = api('POST', '/sops/' + sop_id + '/publish', {'revision': 1})
assert v1['version'] == 1 and v1['content'] == preview['content']
assert v1['sop_id'] == v1['content']['sop_id'] and v1['published_at'].endswith('Z')
filtered = api('GET', '/sops?domain=Billing&risk=medium')
assert sop_id in [s['sop_id'] for s in filtered]
assert [s['sop_id'] for s in filtered] == sorted(s['sop_id'] for s in filtered)
assert sop_id not in [s['sop_id'] for s in api('GET', '/sops?domain=Support&risk=medium')]
print('PASS AC-E2E-001: saved, validated, published version 1 and filtered')

invalid = source.replace('  max_amount: 200\n', '').replace('escalation:\n  - action_id: A1\n    input: refund_amount\n    op: gt\n    amount: 200\n    target_action_id: A2', 'escalation: []')
issues = api('POST', '/validate', {'source': invalid})
assert not issues['valid'] and issues['content'] is None
assert {'REFUND_LIMIT', 'REFUND_ESCALATION'} <= {i['code'] for i in issues['issues']}
assert api('GET', '/drafts/' + sop_id)['revision'] == 1
assert api('PUT', '/drafts/' + sop_id, {'source': invalid})['revision'] == 2
api('POST', '/sops/' + sop_id + '/publish', {'revision': 1}, expected=409)
rejected = api('POST', '/sops/' + sop_id + '/publish', {'revision': 2}, expected=422)
assert {'REFUND_LIMIT', 'REFUND_ESCALATION'} <= {i['code'] for i in rejected['issues']}
assert api('GET', '/drafts/' + sop_id)['failed_revision'] == 2
assert api('GET', '/sops/' + sop_id, user='demo-consumer') == v1
print('PASS AC-E2E-002: independent safety issues and rejected publication')

api('PUT', '/drafts/' + sop_id, {'source': source}, user='demo-consumer', expected=403)
api('POST', '/validate', {'source': source}, user='demo-consumer', expected=403)
api('POST', '/sops/' + sop_id + '/publish', {'revision': 2}, user='demo-consumer', expected=403)
api('GET', '/drafts', user='demo-consumer', expected=403)
api('GET', '/sops/' + sop_id + '/versions/1', user='demo-consumer', expected=403)
assert api('GET', '/sops/' + sop_id, user='demo-consumer') == v1
print('PASS AC-E2E-003 API: consumer snapshot matches and mutations/drafts/history denied; UI consistency is also tested by frontend tests')

restart()
assert api('GET', '/drafts/' + sop_id)['source'] == invalid
assert api('GET', '/drafts/' + sop_id)['failed_revision'] == 2
assert api('GET', '/sops/' + sop_id, user='demo-consumer') == v1
corrected = source.replace('Refund for Duplicate Charge', 'Revised duplicate charge policy')
assert api('PUT', '/drafts/' + sop_id, {'source': corrected})['revision'] == 3
assert api('GET', '/drafts/' + sop_id)['failed_revision'] is None
v2 = api('POST', '/sops/' + sop_id + '/publish', {'revision': 3})
assert v2['version'] == 2 and v2['content']['title'] == 'Revised duplicate charge policy'
assert api('GET', '/sops/' + sop_id + '/versions/1') == v1
api('POST', '/sops/' + sop_id + '/publish', {'revision': 3}, expected=409)
print('PASS AC-E2E-004: failed replacement retained version 1; correction published version 2; history unchanged')
restart()
assert api('GET', '/drafts/' + sop_id)['source'] == corrected
assert api('GET', '/sops/' + sop_id, user='demo-consumer') == v2
assert api('GET', '/sops/' + sop_id + '/versions/1') == v1
assert api('GET', '/drafts/BILL-REFUND-001') == seed
healthy()
print('PASS AC-E2E-005: three healthy services, seed preserved, saved source/failure/current/history survived restarts')
print('Smoke passed for ' + sop_id)
