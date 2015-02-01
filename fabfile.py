from fabric.api import run, sudo, env, task, local, put, parallel
from fabric.context_managers import cd
from fabric.contrib.console import confirm
import logging
from fabtools import require
import requests
import time

env.user = 'root'
env.use_ssh_config = True
folder = "/var/www/hackersome/"

PACKAGES = ['openjdk-7-jre-headless','python-pip','dstat','htop','supervisor',
            'libjna-java', 'libopts25','ntp', 'python-support']

@task
def install_packages():
    require.deb.package(PACKAGES)


@task
def command_nginx(command='reload'):
    run("service nginx {}".format(command))

@task
def cassax(command='status'):
    run("service cassandra {}".format(command))

@task
@parallel
def runx(command):
    run("{}".format(command))


@task
def aptupdate():
  sudo("apt-get update --fix-missing")


@task
def set_up():
  sudo("adduser cassa --system")
  sudo("apt-get update --fix-missing")
  require.deb.packages(PACKAGES)

@task
def generate_cassandra_settings(ip_address):
  text = open("conf/cassandra.yaml").read()
  text = text.replace("<ip_address>",ip_address)

  with open("conf/.cassandra.yaml.temp", "w") as f:
    f.write(text)

  put("conf/.cassandra.yaml.temp", "/etc/cassandra/cassandra.yaml")

@task
def cassandra(ip_address):
  if not confirm("Are you sure to install followings ? \n{}".format("=>\n".join([e for e in env.hosts]))):
    return 

  sudo("curl -L http://debian.datastax.com/debian/repo_key | sudo apt-key add -")
  sudo('echo "deb http://debian.datastax.com/community stable main" > /etc/apt/sources.list.d/datastax.list')
  aptupdate()
  
  sudo("apt-get install -y cassandra=2.0.10 dsc20=2.0.10-1")

  sudo("service cassandra stop")
  sudo("rm -rf /var/lib/cassandra/data/system/*")

  put("conf/log4j-server.properties", "/etc/cassandra/log4j-server.properties")
  sudo("mv /etc/cassandra/cassandra.yaml /etc/cassandra/cassandra2.yaml")

  generate_cassandra_settings(ip_address)

  

@task
def cassandra_agent(datastax_master):
  require.deb.package(["datastax-agent"])
  sudo('echo "stomp_interface: {}" | sudo tee -a /var/lib/datastax-agent/conf/address.yaml'.format(datastax_master))
  sudo("service datastax-agent start")


@task
def rpm(license_key):
  sudo("echo deb http://apt.newrelic.com/debian/ newrelic non-free >> /etc/apt/sources.list.d/newrelic.list")
  sudo("wget -O- https://download.newrelic.com/548C16BF.gpg | apt-key add -")
  aptupdate()
  sudo("apt-get install newrelic-sysmond")
  sudo("nrsysmond-config --set license_key={}".format(license_key))
  sudo("/etc/init.d/newrelic-sysmond start")


@task
def compile():
  local("lein uberjar")

@task
def release(compile_app=True):
  deploy()

@task
def deploy_assets():
    with cd(folder):
        put("resources/public/css/style.css", "public/css/style.css")
        put("resources/public/js/app.js", "public/js/app.js")
        put("logback.xml", "logback.xml")

@task
def deploy(git_version=None):
    uberjar_template = "https://s3-us-west-1.amazonaws.com/hackersome.public/releases/{}/hsm.jar"

    if git_version is None:
        git_version = local("git rev-parse HEAD",capture=True)

    uberjar = uberjar_template.format(git_version)

    rez = requests.head(uberjar)
    assert rez.ok, "Uberjar not found {}\n{}".format(rez.status_code, uberjar)

    sudo("mkdir -p {}".format(folder))

    deploy_assets()

    with cd(folder):
        sudo("wget {}".format(uberjar))
        try:
            sudo("mv hackersome.jar hackersome-old.jar")
        except:
            pass

    sudo("mv hsm.jar hackersome.jar")

    sudo("supervisorctl restart prod_hackersome")

    time.sleep(10)

    def req():
        sudo("http head localhost:8080/")
        sudo("http head localhost:8080/open-source/")

    tries = 0
    while tries < 5:
        try:
            req()
            return True
        except:
            logging.warn("Retrying....")
            tries += 1

        time.sleep(tries*2)





@task
def hostname():
    run("hostname")


@task
def disk_status():
    run("df -h")

@task
def nlog():
    with cd("/var/log/nginx/hackersome.com"):
        run("tail -f access.log")