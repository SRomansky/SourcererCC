import requests
# put the server to sleep
#r = requests.post("http://localhost:4567/halt", data={})
r = requests.post("http://localhost:4568/query/local", data={})
print r.text
