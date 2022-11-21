import sys
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

yamlText = """\
# example
name:
  # details
  family: Smith   # very common
  given: Alice    # one of the siblings
"""

yaml = YAML()
code = yaml.load(yamlText)
code['name']['given'] = 'Bob'
code['name']['me'] = 'panweiji'
src_data = {'user': {'name': '潘维吉', 'age': 18, 'money': None, 'gender': True},
            'lovers': ['柠檬小姐姐', '橘子小姐姐', '小可可']
            }
code['name']['item'] = []
code['name']['item'].append(src_data)
yaml.dump(code, sys.stdout)

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
