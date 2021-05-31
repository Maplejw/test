# API DOC
本文档主要描述web的接口数据获取,所有返回的数据格式为json格式

## 通用的返回数据格式

## 1.获取首页品牌数据
其中data的字段如下

| 字段|类型|是否必传|描述 | 

| event|String|Y|tag_browse |
|tag_id|String|Y|标签ID|
|time|int|Y|浏览时长,单位秒|
|templates|int|Y|浏览模板数量|
| timestamp |int|Y|13位的unix时间戳-精确到毫秒 | 
