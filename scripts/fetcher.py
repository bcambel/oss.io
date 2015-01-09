import json
import requests
import sys

session = requests.Session()

def fetch_user(user, mode):
  
  if mode != '':
    url = "http://hackersome.com/user2/{}/{}?json=1".format(user, mode)
  else:
    url = "http://hackersome.com/user2/{}?json=1".format(user)

  print "Fetching url", url

  r = session.get(url)

  print r.json()

  obj = r.json()

  if not obj or mode == "":
    return

  for o in obj:
    print "Getting " + o
    session.get("http://hackersome.com/user2/{}?json=1".format(o))


if __name__ == "__main__":
  fetch_user(sys.argv[1], sys.argv[2] if len(sys.argv) >= 3 else "")