import ssl
import http.server
import sys

if len(sys.argv) < 5:
    print("Usage: python3 ocsp_stapling_server.py <cert> <key> <ocsp_resp> <port>")
    sys.exit(1)

cert_file = sys.argv[1]
key_file = sys.argv[2]
ocsp_file = sys.argv[3]
port = int(sys.argv[4])

context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(cert_file, key_file)

with open(ocsp_file, 'rb') as f:
    ocsp_resp = f.read()

# Set the OCSP response to be stapled
try:
    context.set_ocsp_response(ocsp_resp)
    print("OCSP response set successfully.")
except AttributeError:
    print("Error: set_ocsp_response is not supported in this Python version.")
    sys.exit(1)

server_address = ('0.0.0.0', port)
httpd = http.server.HTTPServer(server_address, http.server.SimpleHTTPRequestHandler)
httpd.socket = context.wrap_socket(httpd.socket, server_side=True)

print(f"Starting server on port {port} with OCSP stapling...")
try:
    httpd.serve_forever()
except KeyboardInterrupt:
    print("\nServer stopped.")
    sys.exit(0)
