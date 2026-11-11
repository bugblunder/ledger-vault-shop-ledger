#!/usr/bin/env python3
"""Run a short API walkthrough against a local demo instance. Python 3.9+, no dependencies."""
import argparse
import json
import os
from pathlib import Path
import secrets
import urllib.error
import urllib.request

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://localhost:8080')
args = parser.parse_args()
if (root / '.env').exists():
    for line in (root / '.env').read_text().splitlines():
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            os.environ.setdefault(key, value)
# Avoid corporate proxy interception of this explicitly selected local demo.
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
lines = []
def show(message):
    print(message, flush=True)
    lines.append(message)

def call(method, path, body=None, token=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(args.base_url.rstrip('/') + path,
        data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        response = opener.open(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        if response.status != expected:
            raise RuntimeError(f'{method} {path}: expected {expected}, got {response.status}; {raw.decode()[:250]}')
        return json.loads(raw) if raw else None

def login(username, password):
    return call('POST', '/api/auth/signin', {'username': username, 'password': password})['token']

def balance(customer_id, token):
    return call('GET', f'/api/customers/{customer_id}/balance', token=token)['netBalance']

admin_user = os.environ.get('BOOTSTRAP_ADMIN_USERNAME')
admin_password = os.environ.get('BOOTSTRAP_ADMIN_PASSWORD')
if not admin_user or not admin_password:
    raise SystemExit('Run scripts/init_env.py first, or set BOOTSTRAP_ADMIN_USERNAME and BOOTSTRAP_ADMIN_PASSWORD.')
admin = login(admin_user, admin_password)
suffix = secrets.token_hex(3)
owner_name, staff_name = 'demo-owner-' + suffix, 'demo-staff-' + suffix
owner_password, staff_password = 'A1' + secrets.token_urlsafe(24), 'A1' + secrets.token_urlsafe(24)
call('POST', '/api/admin/owners', {'username': owner_name, 'email': owner_name + '@example.test',
    'password': owner_password, 'phoneNumber': '9876543210'}, admin)
owner = login(owner_name, owner_password)
shop = call('POST', '/api/owner/shops', {'name': 'Demo stationery shop', 'contactNumber': '9876543210'}, owner)
staff = call('POST', '/api/owner/staff', {'username': staff_name, 'email': staff_name + '@example.test',
    'password': staff_password, 'phoneNumber': '9876543211', 'role': 'DATA_ENTRY'}, owner)
call('PUT', f'/api/owner/shops/{shop["id"]}/staff/{staff["id"]}', token=owner)
staff_token = login(staff_name, staff_password)
customer = call('POST', '/api/customers', {'name': 'Demo customer', 'shopId': shop['id']}, owner)
cid = customer['id']
show('1. Created an owner, shop, assigned staff member, and customer.')
debit = call('POST', f'/api/customers/{cid}/ledger-entries', {'entryType': 'DEBIT', 'amount': 100, 'description': 'Invoice 001'}, owner)
call('POST', f'/api/customers/{cid}/ledger-entries', {'entryType': 'CREDIT', 'amount': 40, 'description': 'Receipt 001'}, staff_token)
assert balance(cid, owner) == 60
show('2. Invoice 100.00 - staff-recorded payment 40.00 = outstanding 60.00.')
call('POST', f'/api/customers/{cid}/ledger-entries', {'entryType': 'CREDIT', 'amount': 61}, staff_token, expected=400)
show('3. Payment above the outstanding balance was rejected (HTTP 400).')
corrected = call('POST', f'/api/customers/ledger-entries/{debit["id"]}/corrections', {
    'replacement': {'entryType': 'DEBIT', 'amount': 120, 'description': 'Corrected invoice 001'},
    'reason': 'Receipt review found a missing 20.00 item'}, owner)
history = call('GET', f'/api/customers/{cid}/ledger-entries', token=owner)
assert len(history) == 4
assert any(e['id'] == debit['id'] and e['amount'] == 100 for e in history)
assert corrected['reversal']['reversesEntryId'] == debit['id']
assert corrected['replacement']['replacesEntryId'] == debit['id']
assert balance(cid, owner) == 80
show('4. Corrected invoice to 120.00: original + linked reversal + replacement retained; balance 80.00.')
call('POST', f'/api/customers/ledger-entries/{debit["id"]}/reversals', {'reason': 'Second attempt'}, owner, expected=409)
show('5. A second reversal of the same entry was rejected (HTTP 409).')
call('POST', f'/api/owner/staff/{staff["id"]}/archive', {'reason': 'Staff member left the shop'}, owner)
call('GET', '/api/profile/me', token=staff_token, expected=401)
assert balance(cid, owner) == 80
show('6. Archived staff: their token stopped working (HTTP 401); payment and balance remained intact.')
call('POST', f'/api/customers/{cid}/archive', {'reason': 'Account closed for new business'}, owner)
call('POST', f'/api/customers/{cid}/ledger-entries', {'entryType': 'DEBIT', 'amount': 1}, owner, expected=409)
assert balance(cid, owner) == 80
show('7. Archived customer: history still readable; new transactions rejected (HTTP 409).')
call('POST', f'/api/owner/shops/{shop["id"]}/archive', {'reason': 'Demo completed'}, owner)
show('PASS: financial history, correction lineage, permissions, and archival verified over HTTP.')
(root / 'docs' / 'demo-output.txt').write_text('\n'.join(lines) + '\n')
