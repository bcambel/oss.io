from fabric.api import run, sudo, env, task, local, put, parallel
from fabric.context_managers import cd
from fabric.contrib.console import confirm
import logging
from fabtools import require
import requests
import time

logging.basicConfig(format='[%(asctime)s](%(filename)s#%(lineno)d)%(levelname)-7s %(message)s',
                    level=logging.INFO)

# env.user = 'ubuntu'
env.use_ssh_config = True
folder = "/opt/hackersome/"
asset_folder = "/var/www/hackersome/"
PACKAGES = ['openjdk-7-jre-headless','python-pip','dstat','htop','supervisor',
            'libjna-java', 'libopts25','ntp', 'python-support',]

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
@parallel
def install_redis():
    require.deb.package(['tcl8.5'])
    sudo("wget http://download.redis.io/releases/redis-2.8.19.tar.gz")
    sudo("tar xzf redis-2.8.19.tar.gz")
    with cd("redis-2.8.19"):
        sudo("make")
        sudo("make test")
        sudo("make install")
    with cd("redis-2.8.19/utils"):
        sudo("./install_server.sh")

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
    with cd(asset_folder):
        put("resources/public/css/style.css", "public/css/style.css", use_sudo=True)
        put("resources/public/js/app.js", "public/js/app.js", use_sudo=True)
        put("logback.xml", "logback.xml", use_sudo=True)
        put("opt-out.txt", "opt-out.txt", use_sudo=True)

@task
def build(token,new_build=False):

    build_url = "http://admin:%s@jenkins.hackersome.com/{}" % token
    logging.warn("Triggering build using {}".format(token))

    url = "http://jenkins.hackersome.com/buildByToken/build?job=hackersome&token=token123456"
    logging.warn(url)

    if new_build:
        build_req = requests.get(url)

        if not build_req.ok:
            logging.warn("Something went wrong..")
            logging.error("%s - ",build_req.status_code)
            return False
        else:
            logging.warn(build_req.text)

        time.sleep(10)

    project_status_resp = requests.get(build_url.format("job/Hackersome/api/json"))
    logging.warn("PROJECT %s", project_status_resp.status_code)
    if project_status_resp.ok:
        project_status = project_status_resp.json()
        build_number = project_status.get("lastBuild",{}).get("number", -1)
        logging.warn(project_status.get("lastBuild"))
    else:
        logging.warn(project_status_resp.text)
        return False

    # build_number = 11
    session = requests.Session()

    def req():
        succeed = False;building = True;status = "?"
        build_status_resp = session.get(build_url.format("job/Hackersome/{}/api/json".format(build_number)))
        # logging.warn(build_status_resp.text)
        if project_status_resp.ok:
            build_stat = build_status_resp.json()
            logging.warn("BUILD {id} - STATUS [{result}] in {duration}ms Progress?={building}  ".format(**build_stat))
            succeed = build_stat.get("result") == "SUCCESS"
            building = build_stat.get("building", True)
            status = build_stat.get("result")

        return succeed, building, status

    tries = 0
    succeed = False
    building = True
    status = ""

    while building and (not succeed) and tries < 10:
        try:
            succeed, building, status = req()
            if not building:
                break
        except Exception,ex:
            logging.error(ex)

        logging.warn("Retrying.... %d", tries)
        tries += 1

        time.sleep(tries*3)

    logging.info("To the next level now.. %s %s %s", succeed, building, status )
    # if succeed:
    #     if confirm("Would you like to deploy now ?"):
    #         deploy()



def check_jar(uberjar_location):
    rez = requests.head(uberjar_location)
    logging.warn("Uberjar found {}\n{}".format(rez.status_code, uberjar_location))
    return rez.ok


@task
@parallel
def deploy(git_version=None):
    uberjar_template = "https://s3-us-west-1.amazonaws.com/hackersome.public/releases/{}/hsm.jar"

    if git_version is None:
        git_version = local("git rev-parse HEAD",capture=True)

    uberjar = uberjar_template.format(git_version)

    if not check_jar(uberjar):
        return False

    sudo("supervisorctl stop prod_hackersome")

    sudo("mkdir -p {}public/css".format(folder))
    sudo("mkdir -p {}public/js".format(folder))

    deploy_assets()

    with cd(folder):
        sudo("wget {}".format(uberjar))
        try:
            sudo("mv hackersome.jar hackersome-old.jar")
        except:
            pass

        sudo("mv hsm.jar hackersome.jar")

        sudo("echo %s > VERSION" % git_version )

    sudo("supervisorctl start prod_hackersome")
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
            sudo("tail -10 /var/log/syslog")

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

@task
def redis_keys():
    run("redis-cli hgetall oss.system.github_keys")

@task
def setup_machine():

    run("wget https://gist.githubusercontent.com/bcambel/18bdd02ec71a6b8dc9bce035a8c0c61d/raw/5bf14bf8fc2f2a41a711ac5b14640d050a4a013d/setup.sh")
    run("chmod +x setup.sh")
    run(". ./setup.sh")

@task
def setup_serv():
    put("app.worker.basic.ini", "app.ini")
    sudo("supervisorctl reread")
    sudo("supervisorctl update")

@task
def setup_nginx():
    sudo("service nginx restart")

@task
def deploy_code(version="0.1.9"):
    ff = "hackersome-{0}-standalone.jar".format(version)
    put("target/"+ff, ff)
    run("ln -s {} hackersome.jar --force".format(ff))

@task
def restart():
    sudo("supervisorctl restart oss")
    sudo("supervisorctl status oss")
