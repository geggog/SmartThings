import BaseHTTPServer
import subprocess

class MyHandler(BaseHTTPServer.BaseHTTPRequestHandler):

    def do_GET(s):
        s.send_response(200)
        s.send_header("Content-type", "text/html")
        s.end_headers()

        if s.path == '/shutdown':
           s.wfile.write("<html><body>Shutting down ...</body></html>")
           subprocess.call("/usr/local/bin/shutdown.sh", shell=True)
        elif s.path == '/ping':
           s.wfile.write("<html><body>Pong!</body></html>")

if __name__ == '__main__':

    server_class = BaseHTTPServer.HTTPServer
    httpd = server_class(('0.0.0.0', 58000), MyHandler)
    httpd.serve_forever()