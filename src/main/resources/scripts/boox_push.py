#!/usr/bin/env python3
"""Boox Push helper - called by Jelu backend"""
import sys
import json
import os

# Add the cloned repo to path
sys.path.insert(0, '/opt/jelu-data/boox-scripts')

from boox import Boox, read_config

CONFIG_PATH = '/opt/jelu-data/config/boox.ini'

def request_code(email, cloud='push.boox.com'):
    """Request verification code"""
    import requests
    headers = {'Content-Type': 'application/json;charset=utf-8'}
    r = requests.post(f'https://{cloud}/api/1/users/sendMobileCode',
                      headers=headers,
                      data=json.dumps({"mobi": email}))
    print(json.dumps(r.json()))

def obtain_token(email, code, cloud='push.boox.com'):
    """Exchange verification code for token"""
    import requests
    headers = {'Content-Type': 'application/json;charset=utf-8'}
    r = requests.post(f'https://{cloud}/api/1/users/signupByPhoneOrEmail',
                      headers=headers,
                      data=json.dumps({"mobi": email, "code": code}))
    result = r.json()
    if 'data' in result and 'token' in result['data']:
        token = result['data']['token']
        # Save to config
        import configparser
        config = configparser.ConfigParser()
        config['default'] = {
            'email': email,
            'cloud': cloud,
            'token': token
        }
        os.makedirs(os.path.dirname(CONFIG_PATH), exist_ok=True)
        with open(CONFIG_PATH, 'w') as f:
            config.write(f)
        print(json.dumps({"success": True, "token": token[:10] + "..."}))
    else:
        print(json.dumps({"success": False, "error": json.dumps(result)}))

def send_file(filepath):
    """Push a file to Boox"""
    import configparser
    config = configparser.ConfigParser()
    config.read(CONFIG_PATH)
    if not config['default'].get('token'):
        print(json.dumps({"success": False, "error": "No token configured"}))
        return
    try:
        boox = Boox(config)
        boox.send_file(filepath)
        print(json.dumps({"success": True}))
    except Exception as e:
        print(json.dumps({"success": False, "error": str(e)}))

def check_status():
    """Check if configured"""
    import configparser
    config = configparser.ConfigParser()
    config.read(CONFIG_PATH)
    try:
        token = config['default'].get('token', '')
        email = config['default'].get('email', '')
        has_token = bool(token)
        print(json.dumps({"configured": has_token, "email": email}))
    except:
        print(json.dumps({"configured": False, "email": ""}))

if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'status'
    if cmd == 'request-code':
        request_code(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else 'push.boox.com')
    elif cmd == 'obtain-token':
        obtain_token(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else 'push.boox.com')
    elif cmd == 'send':
        send_file(sys.argv[2])
    elif cmd == 'status':
        check_status()
