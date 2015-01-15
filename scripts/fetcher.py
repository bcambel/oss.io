import json
import requests
import sys
import time

session = requests.Session()

def fetch_user(user, mode=None, sleep=0):
  
  if mode is None or mode == '':
    url = "http://hackersome.com/user2/{}?json=1".format(user)
  else:
    url = "http://hackersome.com/user2/{}/{}?json=1".format(user, mode)

  print "Fetching url", url

  r = session.get(url)

  print r.json()

  obj = r.json()

  if not obj or mode == "":
    return

  for o in obj:
    print "Getting " + o
    session.get("http://hackersome.com/user2/{}?json=1".format(o))
    time.sleep(sleep)


if __name__ == "__main__":
  user,mode,sleep= None, None, 0

  if len(sys.argv) == 2:
    _, user, mode = sys.argv
  if len(sys.argv) == 3:
    _, user, mode = sys.argv
  if len(sys.argv) == 4:
    _, user, mode, sleep = sys.argv

  fetch_user(user, mode, float(sleep))