# API DOC
本文档主要描述web的接口数据获取,所有返回的数据格式为json格式.域名待定,

## 请求方式
以下接口描述的请求方式都采用POST的form-data的方式进行接口数据请求

## 通用的返回数据格式
描述了所有的接口的通用返回数据格式，其中data字段里的数据为各个接口业务所特有的，由各个接口进行规定描述.

其中code=0的时候，即代表接口请求成功，然后接口所请求的数据，都在data字段，这个时候无message字段

但code!=0的时候，即代表请求失败，这个时候无data字段，message字段为请求失败的原因。
|字段|类型|是否必传|描述|  
|------|---|---|---|
|code|int|Y|返回的code码|
|data|json数组|N|json数组的字符串,各个接口拥有自己的字段|
|message|string|N|当code!=0的时候，返回的错误消息提示|


## 1.获取首页品牌数据
请求url: /car/get_brand

请求参数：无

返回参数:
|字段|类型|是否必传|描述|  
|------|---|---|---|
|id|int|Y|汽车品牌ID|
|brand|string|Y|汽车品牌名称|
|logo|string|Y|汽车品牌logo图片|
```
{
    "code" : 0,
	  "data": [
	      {"id":1,"brand":"bmw","logo":"/img/a.jpg"},
		    {"id":2,"brand":"toyota","logo":"/img/b.jpg"}
	   ]
}
```


## 2.获取某品牌下的汽车
请求url: /car/get_brand_car

请求参数：
|字段|类型|是否必传|描述|  
|------|---|---|---|
|id|int|Y|汽车品牌ID|

返回参数:
|字段|类型|是否必传|描述|  
|------|---|---|---|
|id|int|Y|汽车ID|
|car_name|string|Y|汽车名称|
|pic|string|Y|汽车图片|
|min|double|N|汽车最低价|
|max|double|N|汽车最高价|

```
{
    "code" : 0,
	"data": [
	    {"id":1,"car_name":"x1","pic":"/img/a.jpg","min":20.11,"max":99.99},
		{"id":2,"car_name":"x3","pic":"/img/b.jpg","min":21.11,"max":99.99}
	]
}
```

## 3.获取汽车的详细数据
请求url: /car/get_car

请求参数：
|字段|类型|是否必传|描述|  
|------|---|---|---|
|id|int|Y|汽车ID|

返回参数:
|字段|类型|是否必传|描述|  
|------|---|---|---|
|pic_list|json数组|Y|汽车的轮播图片，有多张|
|car_name|string|Y|汽车名称|
|min|double|N|汽车最低价|
|max|double|N|汽车最高价|
|car_year|json对象|Y|汽车的具体车型,具体字段类型参考返回示例|


```
{
    "code" : 0,
	"data": {
             "pic_list":["a.jpg","b.jpg"],
             "car_name":"x1",
             "min": 20.11,
             "max": 99.99,
             "car_year" : {
                "2021": [
                    {"min":10.11,"promotion":2,"car_model":"运动版","is_sale":1},
                    {"min":11.11,"promotion":1,"car_model":"豪华版","is_sale":1}
                 ],
                "2020": [
                    {"min":8.11,"promotion":2,"car_model":"运动版","is_sale":1},
                    {"min":9.11,"promotion":1,"car_model":"豪华版","is_sale":1}
                 ]
             }
         }
}
```
