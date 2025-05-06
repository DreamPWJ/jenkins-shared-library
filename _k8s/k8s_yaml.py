import sys
import argparse
from ruamel.yaml import YAML

"""
  @author 潘维吉
  @date 2022/11/19 13:22
  @email 406798106@qq.com
  @description Python语言动态控制K8s集群yaml定义文件
  参考文章: https://yaml.readthedocs.io/en/latest/example.html 、 http://testingpai.com/article/1595507236293
  使用官方pypi源来安装
  pip install ruamel.yaml
"""

# 传递参数  执行示例  python k8s_yaml.py 1,2,3
# arg1 = sys.argv[1]
# argArray = [int(arg1.split(","))]
# print arg1

# 传递参数  执行示例  python k8s_yaml.py  --nfs_params=A,B,C --volume_mounts=AAA/name:BBB/path,CCC:BBB,DDD:BBB
parser = argparse.ArgumentParser(description='manual to this script')
parser.add_argument('--k8s_yaml_file', type=str, default="k8s.yaml")
parser.add_argument('--volume_mounts', type=str, default=None)
parser.add_argument('--nfs_server', type=str, default=None)
parser.add_argument('--nfs_params', type=str, default=None)
parser.add_argument('--default_port', type=int, default=None)
parser.add_argument('--remote_debug_port', type=int, default=None)
parser.add_argument('--is_use_session', type=bool, default=False)
parser.add_argument('--set_custom_startup_command', type=str, default=None)
parser.add_argument('--set_yaml_args', type=str, default=None)
parser.add_argument('--set_python_start_file', type=str, default=None)
parser.add_argument('--is_k8s_health_probe', type=bool, default=False)

args = parser.parse_args()

k8s_yaml_file = args.k8s_yaml_file
volume_mounts = args.volume_mounts

# 挂载映射参数
volume_mounts_yaml = []
volume_host_mounts_yaml = []
if volume_mounts is not None:
    print(volume_mounts)
    volume_mounts_array = volume_mounts.split(",")
    for index in range(len(volume_mounts_array)):
        volume_mounts_name = "volume-mounts-" + str(index)
        volume_mounts_path_array = volume_mounts_array[index].strip().split(":")
        volume_mounts_host_path = volume_mounts_path_array[0]
        volume_mounts_path = volume_mounts_path_array[1]
        volume_mounts_yaml.append({"name": volume_mounts_name, "mountPath": volume_mounts_path})
        volume_host_mounts_yaml.append({"name": volume_mounts_name, "hostPath": {"path": volume_mounts_host_path}})

# NFS网络文件系统参数
nfs_server = args.nfs_server
nfs_params = args.nfs_params
nsf_mount_yaml = []
nsf_server_yaml = []
if nfs_params is not None:
    print(nfs_params)
    nfs_params_array = nfs_params.split(",")
    for index in range(len(nfs_params_array)):
        nfs_name = "nfs-storage-" + str(index)  # 数组方式做多NFS目录映射
        nfs_path_array = nfs_params_array[index].strip().split(":")
        nfs_mount_path = nfs_path_array[0]
        nfs_server_path = nfs_path_array[1]
        nsf_mount_yaml.append({"name": nfs_name, "mountPath": nfs_mount_path})
        # readOnly设置False 而 NFS服务器是只读模式 可能导致Pod无法启动
        nsf_server_yaml.append(
            {"name": nfs_name, "nfs": {"server": nfs_server, "path": nfs_server_path, "readOnly": False}})

# 业务应用是否使用Session
is_use_session = args.is_use_session
session_yaml = None
if is_use_session:
    print(is_use_session)
    session_yaml = {"sessionAffinity": "ClientIP", "sessionAffinityConfig": {"clientIP": {"timeoutSeconds": 10800}}}

# 第一步: 创建YAML对象
yaml = YAML()  # typ='safe' 导致生成的yaml文件和原顺序不一致
yaml.default_flow_style = False  # 按原风格输出

# 第二步: 读取yaml格式的文件
# with open('kubernetes.yaml', encoding='utf-8') as file:
# data = yaml.load(file)  # 为列表类型

print(k8s_yaml_file)
yamlContent = list(yaml.load_all(open(k8s_yaml_file)))  # 多文档结构读取---分割的yaml文件
# print(yamlContent)

yaml_containers = yamlContent[0]['spec']['template']['spec']['containers']
yaml_volume_mounts = yaml_containers[0]['volumeMounts'] = []
yaml_volume = yamlContent[0]['spec']['template']['spec']['volumes'] = []

# 挂载映射参数处理
if volume_mounts is not None:
    yaml_volume_mounts.extend(
        [*volume_mounts_yaml]
    )
    yaml_volume.extend(
        [*volume_host_mounts_yaml]
    )

# NFS网络文件系统参数处理
if nfs_params is not None:
    yaml_volume_mounts.extend(
        [*nsf_mount_yaml]
    )
    yaml_volume.extend(
        [*nsf_server_yaml]
    )

# 自定义启动命令
set_custom_startup_command = args.set_custom_startup_command
if set_custom_startup_command is not None:
    print(set_custom_startup_command)
    yaml_containers[0]["command"] = [set_custom_startup_command]  # 覆盖或补充 ENTRYPOINT 或 CMD
    # yaml_containers[0]["command"] = ["java"]  # 覆盖或补充 ENTRYPOINT 或 CMD
    # yaml_containers[0]["args"] = ["-jar", "-Xms128m",
    #                               "-Dfile.encoding=UTF-8", "/app/ListenWebService.jar"]

# Java动态设置k8s  yaml args参数
set_yaml_args = args.set_yaml_args
if set_yaml_args is not None and set_custom_startup_command is None :
    print(set_yaml_args)
    # 适配Java Spring Boot框架容器动态启动命令  比如 JVM堆栈内存控制
    yaml_containers[0]["command"] = ["java"]  # 覆盖或补充 ENTRYPOINT 或 CMD
    yaml_containers[0]["args"] = ["-jar", "-Xms128m", set_yaml_args,
                                  "-Djava.security.egd=file:/dev/./urandom", "/server.jar"]

# 设置python语言相关的参数
set_python_start_file = args.set_python_start_file
if set_python_start_file is not None and set_custom_startup_command is None :
    print(set_python_start_file)
    # 启动命令
    yaml_containers[0]["command"] = ["python"]  # 覆盖或补充 ENTRYPOINT 或 CMD
    yaml_containers[0]["args"] = [set_python_start_file]

# 业务应用是否使用Session处理
if is_use_session:
    service_spec = yamlContent[1]['spec']
    service_spec['type'] = "NodePort"
    service_spec.update(session_yaml)  # update更新JSON数据

# 默认应用端口 应用服务扩展端口
default_port = args.default_port
if default_port is not None:
    print(default_port)
    yaml_containers[0]['ports'].append({'containerPort': default_port})

# 是否禁止执行K8S默认的健康探测
is_k8s_health_probe = args.is_k8s_health_probe
if is_k8s_health_probe:
    del yaml_containers[0]["readinessProbe"]
    del yaml_containers[0]["livenessProbe"]

# print(yamlContent)

with open(k8s_yaml_file, mode='w', encoding='utf-8') as file:
    yaml.dump(yamlContent[0], file)
    file.write("\n---\n")
    yaml.dump(yamlContent[1], file)

#  示例代码
# yamlText = """\
# # example
# name:
#   # details
#   family: Smith   # very common
#   given: Alice    # one of the siblings
# """
#
# yaml = YAML()
# code = yaml.load(yamlText)
# code['name']['given'] = 'Bob'
# code['name']['me'] = 'panweiji'
# src_data = {'user': {'name': '潘维吉', 'age': 18, 'money': None, 'gender': True},
#             'lovers': ['柠檬小姐姐', '橘子小姐姐', '小可可']
#             }
# code['name']['item'] = []
# code['name']['item'].append(src_data)
# yaml.dump(code, sys.stdout)

# # 第一步: 创建YAML对象
# # yaml = YAML(typ='safe')
# yaml = YAML()
#
# # 第二步: 将Python中的字典类型数据转化为yaml格式的数据
# src_data = {'user': {'name': '可优', 'age': 17, 'money': None, 'gender': True},
#             'lovers': ['柠檬小姐姐', '橘子小姐姐', '小可可']
#             }
#
# with open('new.yaml', mode='w', encoding='utf-8') as file:
#     yaml.dump(src_data, file)
