from fabric.api import run, sudo, env, task, local, put, parallel
from fabric.context_managers import cd
import logging
from fabtools import require

env.user = 'root'
env.use_ssh_config = True
folder = "/var/www/hackersome/"

PACKAGES = []

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
def runx(command):
    run("{}".format(command))


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
def deploy():
  sudo("mkdir -p {}".format(folder))

  version = open("VERSION").readlines()[0]
  jar_file = "target/hsm.jar".format(version)
  put(jar_file, "{}hackersome-latest.jar".format(folder))

  deploy_assets()
  
  with cd(folder):
    try:
      sudo("mv hackersome.jar hackersome-old.jar")
    except:
      pass

    sudo("mv hackersome-latest.jar hackersome.jar")

  sudo("supervisorctl restart prod_hackersome")


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