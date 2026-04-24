#!/usr/bin/env python

from __future__ import print_function

from bs4 import BeautifulSoup
from collections import OrderedDict
import json
import requests
import subprocess
import sys

GNUTLS_URL = 'https://gitlab.com/gnutls/gnutls/raw/master/lib/algorithms/ciphersuites.c'
IANA_URL = 'http://www.iana.org/assignments/tls-parameters/tls-parameters.xhtml'
MOZILLA_SERVER_SIDE_TLS_URL = 'https://raw.githubusercontent.com/mozilla/server-side-tls/gh-pages/Server_Side_TLS.mediawiki'
NSS_URL = 'https://hg.mozilla.org/projects/nss/raw-file/tip/lib/ssl/sslproto.h'
OPENSSL_URL = 'https://raw.githubusercontent.com/openssl/openssl/master/include/openssl/tls1.h'

LIBRARY_ORDER = ['IANA', 'GnuTLS', 'NSS', 'OpenSSL']
COMPAT_ORDER = ['Old', 'Intermediate', 'Modern']

# Styles for the table
#COMPAT_STYLE_NO_MATCH = 'background-color: white;'
COMPAT_STYLE_NO_MATCH = ''
COMPAT_STYLE = {
    'Modern': 'background-color: #99cc00; font-weight: bold;',
    'Intermediate': 'background-color: #336699; font-weight: bold;',
    'Old': 'background-color: #ffcc66; font-weight: bold;'
}

__colorize_lists = {
    'Modern': [],
    'Intermediate': [],
    'Old': []
}

def usage():
    print("""Generate a table of cipher names from all the major library makers.

Usage: {0} <output-format> [--colorize]

Valid output formats are: json, csv, mediawiki""".format(sys.argv[0]))
    sys.exit(1)


# Parse the command line
def parse_args():
    colorize = False

    if len(sys.argv) < 2 or len(sys.argv) > 3:
        usage()
    if 'json' not in sys.argv[1] and 'csv' not in sys.argv[1] and 'mediawiki' not in sys.argv[1]:
        usage()

    if '--colorize' in sys.argv:
        if sys.argv[1] == 'mediawiki':
            colorize = True

    return [sys.argv[1], colorize]

def get_colorize_chart():
    # XXX: should prefer to use code points from IANA_URL
    name_code_points = {}
    try:
        output = subprocess.check_output(['openssl', 'ciphers', '-V'], universal_newlines=True)

        for line in output.split('\n'):
            if '0x' in line:
                fields = line.split()
                name_code_points[fields[2]] = fields[0]
    except:
        print('Unable to run openssl ciphers', file=sys.stderr)
        sys.exit()

    print('Retrieving cipher suites from Mozilla Server Side TLS page', file=sys.stderr)

    # Grab the cipher suites from the Mozilla page
    r = requests.get(MOZILLA_SERVER_SIDE_TLS_URL)

    # Try to grab the ciphersuites out the ugly mess that is a wiki page
    # XXX: should prefer to use structured data from guidelines instead of from wiki page
    recommendations = [];
    for line in r.text.split('\n'):
        if "* Cipher suites (" in line:
            if "'''" in line:
                recommendations.append(line.split("'''")[1])
            else:
                recommendations.append('')

    __colorize_lists.update({
        'Modern': get_colorize_chart_openssl_ciphers(name_code_points, recommendations[0] + ':' + recommendations[1]),
        'Intermediate': get_colorize_chart_openssl_ciphers(name_code_points, recommendations[2] + ':' + recommendations[3]),
        #'Old': get_colorize_chart_openssl_ciphers(name_code_points, recommendations[4] + ':' + recommendations[5])
    })

def get_colorize_chart_openssl_ciphers(name_code_points, ciphersuites):
    code_points = []
    for cipher in ciphersuites.split(':'):
        if cipher in name_code_points:
            code_points.append(name_code_points[cipher])
        # XXX: explifies why input should be taken from IANA and guidelines instead of from wiki pages
        elif cipher == 'ECDHE-ECDSA-AES256-SHA384':
            code_points.append('0xC0,0x24')
        elif cipher == 'ECDHE-RSA-AES256-SHA384':
            code_points.append('0xC0,0x28')
        elif cipher == 'DES-CBC3-SHA':
            code_points.append('0x00,0x0A')
        elif cipher != '':
            print("Warning: not found: {0}".format(cipher))

    return code_points

def get_hex_values():
    # Store all the ciphers away
    cipher_hex_values = OrderedDict()

    # Grab the list from the IANA
    print('Retrieving IANA cipher List', file=sys.stderr)
    try:
        r = requests.get(IANA_URL)
        soup = BeautifulSoup(r.text, 'html.parser')\
            .select('table[id="table-tls-parameters-4"]')[0]\
            .find_all('tbody')[0]

        for row in soup.find_all('tr'):
            columns = [ x.string for x in row.find_all('td') ]

            # For now, we can ignore any IANA entries with '-' or '*' in them
            if '-' not in columns[0] and '*' not in columns[0] and columns[1] != 'Unassigned' and columns[1] != 'Reserved':
                cipher_hex_values[ columns[0] ] = {
                    'GnuTLS': '',
                    'IANA': columns[1],
                    'NSS': '',
                    'OpenSSL': ''
                }

    except Exception as e:
        print('Unable to retrieve or parse IANA cipher list: {}'.format(e), file=sys.stderr)

    # Grab the list from NSS (Mozilla)
    print('Retrieving NSS cipher list', file=sys.stderr)
    try:
        r = requests.get(NSS_URL)
        for line in r.text.split('\n'):
            # A typical line would look like: #define TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256   0xC02F
            if '#define TLS' in line and '0x' in line:
                cipher = line.split()[1]

                hex = line.split()[2].upper()
                code_point = '0x' + hex[2:4] + ',0x' + hex[4:6]

                if code_point in cipher_hex_values:
                    cipher_hex_values[code_point]['NSS'] = cipher
                # 0x00,0x60-66  Reserved to avoid conflicts with widely deployed implementations
                elif not code_point.startswith('0x00,0x6'):
                    print('  Warning: code point {code_point} ({cipher}) not in IANA registry'.format(
                        code_point=code_point, cipher=cipher
                    ), file=sys.stderr)

    except:
        print('Unable to retrieve or parse NSS cipher list', file=sys.stderr)

    # Grab the list from OpenSSL
    print('Retrieving OpenSSL cipher list', file=sys.stderr)
    try:
        # OpenSSL splits up their code points and their text names for them
        openssl_hex_values = {}
        openssl_txt_values = {}

        r = requests.get(OPENSSL_URL)
        for line in r.text.split('\n'):
            if line.startswith('#define TLS1_CK'):
                cipher = line.split()[1].split('TLS1_CK_')[-1]
                hex = line.split()[2]
                code_point = '0x' + hex[6:8] + ',0x' + hex[8:10]

                # e.g., ECDHE_RSA_WITH_AES_128_GCM_SHA256 -> 0x0C,0x2F
                openssl_hex_values[cipher] = code_point
            elif line.startswith('#define TLS1_3_CK'):
                cipher = line.split()[1].split('TLS1_3_CK_')[-1]
                hex = line.split()[2]
                code_point = '0x' + hex[6:8] + ',0x' + hex[8:10]

                # e.g., TLS1_3_CK_AES_128_GCM_SHA256 -> 0x13,0x01
                openssl_hex_values[cipher] = code_point
            elif line.startswith('#define TLS1_TXT'):
                cipher = line.split()[1].split('TLS1_TXT_')[-1]
                text = line.split()[2][1:-1]

                # e.g., ECDHE_RSA_WITH_AES_128_GCM_SHA256 -> ECDHE-RSA-AES128-GCM-SHA256
                openssl_txt_values[cipher] = text
            elif line.startswith('#define TLS1_RFC'):
                cipher = line.split()[1].split('TLS1_RFC_')[-1]
                text = line.split()[2][1:-1]

                # e.g., TLS1_RFC_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256 -> TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
                openssl_txt_values[cipher] = text
            elif line.startswith('#define TLS1_3_RFC'):
                cipher = line.split()[1].split('TLS1_3_RFC_')[-1]
                text = line.split()[2][1:-1]

                # e.g., TLS1_3_RFC_AES_128_GCM_SHA256 -> TLS_AES_128_GCM_SHA256
                openssl_txt_values[cipher] = text

        for key, value in openssl_hex_values.items():
            if value in cipher_hex_values:
                cipher_hex_values[value]['OpenSSL'] = openssl_txt_values[key]
            else:
                print('  Warning: code point {code_point} ({cipher}) not in IANA registry'.format(
                    code_point=value, cipher=key
                ), file=sys.stderr)
    except:
        print('Unable to retrieve or parse OpenSSL cipher list', file=sys.stderr)

    # Grab the list from GnuTLS
    print('Retrieving GnuTLS cipher list', file=sys.stderr)
    try:
        r = requests.get(GNUTLS_URL)

        # Some lines look like: #define GNUTLS_DH_ANON_3DES_EDE_CBC_SHA1 { 0x00, 0x1B }
        # Other look like:      #define GNUTLS_ECDHE_ECDSA_CAMELLIA_128_CBC_SHA256 { 0xC0,0x72 }
        for line in r.text.split('\n'):
            if line.startswith('#define GNUTLS_') and '{' in line:
                cipher = line.split()[1][3:]
                code_point = line.split('{')[-1].replace(' ', '').replace('}', '').upper().replace('X', 'x')

                if code_point in cipher_hex_values:
                    cipher_hex_values[code_point]['GnuTLS'] = cipher
                # 0x00,0x60-66  Reserved to avoid conflicts with widely deployed implementations
                elif not code_point.startswith('0x00,0x6'):
                    print('  Warning: code point {code_point} ({cipher}) not in IANA registry'.format(
                        code_point=code_point, cipher=cipher
                    ), file=sys.stderr)
    except:
        print('Unable to retrieve or parse GnuTLS cipher list', file=sys.stderr)

    print('\n', file=sys.stderr)
    return cipher_hex_values

def __print_wiki_entry(code_point, ciphers):
    print('|-')

    # Determine the style to use; use COMPAT_STYLE_NO_MATCH by default
    style = '| style="{style}" '.format(style=COMPAT_STYLE_NO_MATCH)

    # Now print all the columns for the various libraries
    for order in COMPAT_ORDER:
        if code_point in __colorize_lists[order]:
            style = '| style="{style}" '.format(style=COMPAT_STYLE[order])
            centered_style = '| style="{style} text-align: center;" '.format(style=COMPAT_STYLE[order])

    # Print the Hex column
    print('! scope=row | {code_point}'.format(code_point=code_point))

    # Determine the priority
    if code_point in __colorize_lists.get('Intermediate'):
        priority = __colorize_lists.get('Intermediate').index(code_point) + 1
    else:
        priority = None

    # Print the columns by priority
    if priority:
        print('{style}| {priority}'.format(style=centered_style, priority=priority))
    else:
        print('{style}data-sort-value="1000" | '.format(style=style))


    for library in LIBRARY_ORDER:
        print('{style}| {cipher}'.format(style=style, cipher=ciphers.get(library, '')))

def print_csv(data):
    values = []
    header = ["hex",] + LIBRARY_ORDER
    print(','.join(header))
    for code_point, cypher in data.items():
        r = [code_point.replace(',0x','')]
        r.extend(list(cypher.values()))
        line = ",".join(r)
        print(line)


# Print everything out
def print_output(cipher_hex_values, output_format):
    # JSON output is super easy
    if output_format == 'json':
        print(json.dumps(cipher_hex_values, indent=2))

    elif output_format == 'csv':
        print_csv(cipher_hex_values)

    elif output_format == 'mediawiki':
        # Table header
        print('{| class="wikitable sortable" style="background-color:#fff;"')
        print('|-')
        print('\n'.join(
            ['! scope="col" | ' + x for x in ['Hex', 'Priority'] + LIBRARY_ORDER]
        ))

        # Determine the order to go by: first let's go by priority
        for code_point in __colorize_lists.get('Intermediate'):
            __print_wiki_entry(code_point, cipher_hex_values[code_point])
            del(cipher_hex_values[code_point])

        # If they don't have a priority, then go by hex value
        for code_point, ciphers in reversed(cipher_hex_values.items()):
            __print_wiki_entry(code_point, ciphers)


        # Table footer
        print('|}')


if __name__ == '__main__':
    output_format = parse_args()

    output = get_hex_values()

    if output_format[1]:
        get_colorize_chart()

    print_output(output, output_format[0])