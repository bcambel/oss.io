from fabric.api import run, sudo, env, task, local, put
from fabric.context_managers import cd
import logging
from fabtools import require

env.user = 'root'
env.use_ssh_config = True

PACKAGES = []

@task
def install_packages():
    require.deb.package(PACKAGES)


@task
def command_nginx(command='reload'):
    run("service nginx {}".format(command))


@task
def deploy(compile_app=True):
  if compile_app:
    local("lein uberjar")

  version = open("VERSION").readlines()[0]
  jar_file = "target/hackersome-{}-standalone.jar".format(version)
  put(jar_file, "/var/www/hackersome/hackersome-latest.jar")

  put("resources/public/css/style.css", "/var/www/hackersome/public/css/style.css")
  
  with cd("/var/www/hackersome"):
    sudo("mv hackersome.jar hackersome-old.jar")
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