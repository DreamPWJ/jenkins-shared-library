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

# 传递参数  执行示例  python K8sYaml.py 1,2,3
# arg1 = sys.argv[1]
# argArray = [int(arg1.split(","))]
# print arg1

# 传递参数  执行示例  python K8sYaml.py  --nfs_params=A,B,C --volume_mounts=AAA/name:BBB/path,CCC:BBB,DDD:BBB
parser = argparse.ArgumentParser(description='manual to this script')
parser.add_argument('--k8s_yaml_file', type=str, default="k8s.yaml")
parser.add_argument('--nfs_params', type=str, default=None)
parser.add_argument('--volume_mounts', type=str, default=None)
args = parser.parse_args()

k8s_yaml_file = args.k8s_yaml_file
volume_mounts = args.volume_mounts
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

nfs_params = args.nfs_params
nsf_mount_yaml = None
nsf_server_yaml = None
if nfs_params is not None:
    print(nfs_params)
    nfs_array = nfs_params.split(",")
    nfs_mount_path = nfs_array[0]
    nfs_server = nfs_array[1]
    nfs_server_path = nfs_array[2]
    nfs_name = "nfs-storage"
    nsf_mount_yaml = {"name": nfs_name, "mountPath": nfs_mount_path}
    nsf_server_yaml = {"name": nfs_name, "nfs": {"server": nfs_server, "path": nfs_server_path}}

# 第一步: 创建YAML对象
yaml = YAML()  # typ='safe' 导致生成的yaml文件和原顺序不一致
yaml.default_flow_style = False  # 按原风格输出

# 第二步: 读取yaml格式的文件
# with open('kubernetes.yaml', encoding='utf-8') as file:
# data = yaml.load(file)  # 为列表类型

print(k8s_yaml_file)
yamlContent = list(yaml.load_all(open(k8s_yaml_file)))  # 多文档结构读取---分割的yaml文件
# print(yamlContent)

yaml_volume_mounts = yamlContent[0]['spec']['template']['spec']['containers'][0]['volumeMounts'] = []
yaml_volume = yamlContent[0]['spec']['template']['spec']['volumes'] = []

if volume_mounts is not None:
    yaml_volume_mounts.extend(
        [*volume_mounts_yaml]
    )
    yaml_volume.extend(
        [*volume_host_mounts_yaml]
    )
if nfs_params is not None:
    yaml_volume_mounts.extend(
        [nsf_mount_yaml]
    )
    yaml_volume.extend(
        [nsf_server_yaml]
    )

# print(yamlContent)

with open(k8s_yaml_file, mode='w', encoding='utf-8') as file:
    yaml.dump(yamlContent[0], file)
    file.write("\n---\n")
    yaml.dump(yamlContent[1], file)


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
