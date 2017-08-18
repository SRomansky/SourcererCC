import requests
# put the server to sleep
r = requests.post("http://localhost:4567/halt", data={})
print r.text == u'Stopping process.'
