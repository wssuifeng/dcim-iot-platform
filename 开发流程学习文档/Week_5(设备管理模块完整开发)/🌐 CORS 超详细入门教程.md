# 🌐 CORS 超详细入门教程

---

## 一、先理解什么是「同源」

在讲 CORS 之前，必须先理解 **同源策略（Same-Origin Policy）**，它是 CORS 存在的原因。

### 什么是「源」？

一个网址由三部分组成：

```
https://www.example.com:443/path/page
  ↑         ↑              ↑    ↑
 协议       域名           端口  路径(不重要)
```

**协议 + 域名 + 端口** 三者合在一起，就叫一个 **"源"（Origin）**。

### 什么是「同源」？

两个网址的 **协议、域名、端口** 完全一致，就叫同源。

| 网址A                  | 网址B                     | 是否同源 | 原因                      |
| ---------------------- | ------------------------- | -------- | ------------------------- |
| `http://example.com/a` | `http://example.com/b`    | ✅ 同源   | 协议、域名、端口都一样    |
| `http://example.com`   | `https://example.com`     | ❌ 不同源 | 协议不同（http vs https） |
| `http://example.com`   | `http://www.example.com`  | ❌ 不同源 | 域名不同                  |
| `http://example.com`   | `http://example.com:8080` | ❌ 不同源 | 端口不同                  |

---

## 二、为什么需要同源策略？

想象一个场景：

> 🔴 **危险场景**：你登录了网上银行 `bank.com`，浏览器保存了你的登录 Cookie。
> 然后你点开了一个恶意网站 `evil.com`，这个网站里的 JavaScript 偷偷发了一个请求到 `bank.com`，由于浏览器会自动带上 Cookie，恶意网站就能以你的身份操作你的银行账户！

**同源策略**就是浏览器的安全守卫：**默认情况下，一个源的网页，不允许向另一个源发送请求来读取响应数据**。

> 💡 类比：同源策略就像一栋公寓楼的门禁——住在101房的住户，不能随便拿102房的快递。

---

## 三、那 CORS 到底是什么？

**CORS = Cross-Origin Resource Sharing = 跨源资源共享**

同源策略虽然安全，但太严格了。现实开发中，我们经常需要跨源：

> 📱 前端运行在 `http://localhost:3000`
> 🖥️ 后端运行在 `http://localhost:8080`
>
> 端口不同 → 不同源 → 请求被浏览器拦截！

**CORS 就是浏览器提供的一套「合法翻墙」机制**——让服务端可以告诉浏览器："这个源是可信的，放行吧！"

> 💡 类比：CORS 就像是服务端给浏览器发了一张"通行证"，浏览器看到通行证后，就会允许跨源请求的响应数据通过。

---

## 四、CORS 是怎么工作的？

### 4.1 简单请求（Simple Request）

满足以下**所有条件**的请求叫"简单请求"：

- 方法是 `GET`、`POST` 或 `HEAD`
- Content-Type 只能是 `text/plain`、`multipart/form-data`、`application/x-www-form-urlencoded`
- 没有自定义请求头

**工作流程：**

```
前端 (localhost:3000)                     后端 (localhost:8080)
      |                                        |
      |  ① 发请求，自动带上请求头：              |
      |  Origin: http://localhost:3000          |
      |--------------------------------------->|
      |                                        |
      |  ② 后端返回响应头：                      |
      |  Access-Control-Allow-Origin:           |
      |    http://localhost:3000               |
      |<---------------------------------------|
      |                                        |
      |  ③ 浏览器检查：Origin 是否匹配            |
      |     允许的头中的值？                      |
      |     ✅ 匹配 → 放行，前端拿到数据           |
      |     ❌ 不匹配 → 拦截，前端报 CORS 错误     |
```

### 4.2 预检请求（Preflight Request）

不满足简单请求条件的，浏览器会先发一个 **OPTIONS 请求**，问问服务端"我能不能发这个跨源请求？"

> 最常见触发预检的场景：
> - 使用了自定义请求头（如 `Authorization`）
> - Content-Type 是 `application/json`
> - 使用了 PUT、DELETE 等方法

**工作流程：**

```
前端 (localhost:3000)                     后端 (localhost:8080)
      |                                        |
      |  ① 浏览器先发 OPTIONS 预检请求：          |
      |  OPTIONS /api/data                     |
      |  Origin: http://localhost:3000          |
      |  Access-Control-Request-Method: POST    |
      |  Access-Control-Request-Headers:        |
      |    Content-Type, Authorization          |
      |--------------------------------------->|
      |                                        |
      |  ② 后端返回预检响应：                     |
      |  Access-Control-Allow-Origin:           |
      |    http://localhost:3000               |
      |  Access-Control-Allow-Methods:          |
      |    GET, POST, PUT, DELETE               |
      |  Access-Control-Allow-Headers:          |
      |    Content-Type, Authorization          |
      |<---------------------------------------|
      |                                        |
      |  ③ 浏览器检查预检响应：✅ 通过！           |
      |                                        |
      |  ④ 发送真正的 POST 请求                   |
      |--------------------------------------->|
      |                                        |
      |  ⑤ 后端返回真正的响应                     |
      |<---------------------------------------|
```

> ⚠️ **重点**：预检请求是**浏览器自动发出的**，不需要前端代码手动发。但后端**必须**正确处理 OPTIONS 请求。

---

## 五、关键响应头一览

后端需要在响应中设置这些头：

| 响应头                             | 作用                   | 示例                           |
| ---------------------------------- | ---------------------- | ------------------------------ |
| `Access-Control-Allow-Origin`      | 允许哪些源访问         | `http://localhost:3000` 或 `*` |
| `Access-Control-Allow-Methods`     | 允许哪些HTTP方法       | `GET, POST, PUT, DELETE`       |
| `Access-Control-Allow-Headers`     | 允许哪些请求头         | `Content-Type, Authorization`  |
| `Access-Control-Allow-Credentials` | 是否允许携带Cookie     | `true`                         |
| `Access-Control-Max-Age`           | 预检请求缓存时间（秒） | `86400`                        |

---

## 六、实战：各种后端框架配置 CORS

### 6.1 Node.js + Express

```javascript
const express = require('express');
const app = express();

// ✅ 方式一：手动写中间件
app.use((req, res, next) => {
  // 允许的源
  res.setHeader('Access-Control-Allow-Origin', 'http://localhost:3000');
  // 允许的方法
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE');
  // 允许的请求头
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  // 允许携带 Cookie
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  // 预检缓存时间
  res.setHeader('Access-Control-Max-Age', '86400');

  // 处理预检请求：直接返回 204
  if (req.method === 'OPTIONS') {
    return res.sendStatus(204);
  }

  next();
});

// ✅ 方式二：使用 cors 库（推荐）
const cors = require('cors');

// 简单使用 - 允许所有源
app.use(cors());

// 或者精确配置
app.use(cors({
  origin: 'http://localhost:3000',     // 允许的源
  methods: ['GET', 'POST', 'PUT', 'DELETE'],  // 允许的方法
  allowedHeaders: ['Content-Type', 'Authorization'],  // 允许的头
  credentials: true,                   // 允许 Cookie
  maxAge: 86400,                       // 预检缓存时间
}));

app.get('/api/data', (req, res) => {
  res.json({ message: '获取成功！' });
});

app.listen(8080, () => console.log('Server running on :8080'));
```

### 6.2 Python + Flask

```python
from flask import Flask
from flask_cors import CORS

app = Flask(__name__)

# 简单使用 - 允许所有源
CORS(app)

# 精确配置
CORS(app, resources={
    r"/api/*": {
        "origins": "http://localhost:3000",
        "methods": ["GET", "POST", "PUT", "DELETE"],
        "allow_headers": ["Content-Type", "Authorization"],
        "supports_credentials": True,
        "max_age": 86400
    }
})

@app.route('/api/data')
def get_data():
    return {'message': '获取成功！'}

if __name__ == '__main__':
    app.run(port=8080)
```

### 6.3 Java + Spring Boot

```java
// 方式一：全局配置
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(true)
                .maxAge(86400);
    }
}

// 方式二：在单个 Controller/方法上加注解
@RestController
@CrossOrigin(
    origins = "http://localhost:3000",
    methods = {RequestMethod.GET, RequestMethod.POST},
    allowedHeaders = {"Content-Type", "Authorization"},
    allowCredentials = true
)
public class DataController {
    @GetMapping("/api/data")
    public Map<String, String> getData() {
        return Map.of("message", "获取成功！");
    }
}
```

---

## 七、前端如何配合？

### 7.1 普通 fetch（不携带 Cookie）

```javascript
fetch('http://localhost:8080/api/data')
  .then(res => res.json())
  .then(data => console.log(data))
  .catch(err => console.error('请求失败:', err));
```

### 7.2 携带 Cookie 的 fetch

```javascript
// ⚠️ 携带 Cookie 时，后端不能设置 origin 为 "*"
// 必须指定确切的源地址
fetch('http://localhost:8080/api/data', {
  credentials: 'include'  // 关键！让浏览器带上 Cookie
})
  .then(res => res.json())
  .then(data => console.log(data));
```

### 7.3 使用 Axios

```javascript
import axios from 'axios';

// 携带 Cookie
axios.get('http://localhost:8080/api/data', {
  withCredentials: true  // 关键！等同于 fetch 的 credentials: 'include'
})
  .then(res => console.log(res.data));
```

---

## 八、常见报错与解决

### ❌ 报错 1：`Access-Control-Allow-Origin` 缺失

```
Access to XMLHttpRequest at 'http://localhost:8080/api/data' from origin
'http://localhost:3000' has been blocked by CORS policy: No
'Access-Control-Allow-Origin' header is present on the requested resource.
```

**原因**：后端没有设置 CORS 头。
**解决**：后端添加 `Access-Control-Allow-Origin` 响应头。

---

### ❌ 报错 2：`credentials` 与 `*` 冲突

```
Access to XMLHttpRequest ... has been blocked by CORS policy:
The value of the 'Access-Control-Allow-Origin' header in the response must
not be the wildcard '*' when the request's credentials mode is 'include'.
```

**原因**：前端用了 `credentials: 'include'`，但后端的 `Allow-Origin` 设成了 `*`。
**解决**：后端必须写明具体源地址，不能用 `*`：

```javascript
// ❌ 错误
res.setHeader('Access-Control-Allow-Origin', '*');
res.setHeader('Access-Control-Allow-Credentials', 'true');

// ✅ 正确
res.setHeader('Access-Control-Allow-Origin', 'http://localhost:3000');
res.setHeader('Access-Control-Allow-Credentials', 'true');
```

---

### ❌ 报错 3：预检请求被拦截

```
Access to XMLHttpRequest ... has been blocked by CORS policy: Response to
preflight request doesn't pass access control check.
```

**原因**：后端没有正确处理 OPTIONS 请求，或者没有返回对应的 `Allow-Methods` / `Allow-Headers`。
**解决**：确保后端中间件拦截 OPTIONS 并返回正确的头和 204 状态码。

---

## 九、一图总结 CORS 流程

```
┌──────────┐                          ┌──────────┐
│  前端     │                          │  后端     │
│ :3000    │                          │ :8080    │
└────┬─────┘                          └────┬─────┘
     │                                     │
     │  ① 浏览器判断：是否跨源？              │
     │     是 → 触发 CORS 机制               │
     │                                     │
     │  ② 是简单请求吗？                     │
     │     否 → 先发 OPTIONS 预检 ─────────>│
     │                                     │
     │  <──── 返回 Allow-* 头 (204) ──────│
     │     浏览器检查：通过？                 │
     │                                     │
     │  ③ 发送真正请求 ─────────────────>│
     │     Origin: http://localhost:3000   │
     │                                     │
     │  <──── 返回响应 + Allow-Origin 头 ──│
     │     浏览器检查：Origin 匹配？         │
     │     ✅ 放行 → 前端拿到数据             │
     │     ❌ 拦截 → 控制台报 CORS 错误       │
```

---

## 十、关键记忆点 🧠

| 要点                          | 说明                                            |
| ----------------------------- | ----------------------------------------------- |
| 🛡️ CORS 是**浏览器**的安全机制 | 服务端之间互相请求不受 CORS 限制                |
| 🔑 关键在**后端配置**          | 后端必须返回正确的 `Access-Control-*` 头        |
| 📨 预检请求 = OPTIONS          | 使用自定义头/非简单方法时触发                   |
| 🍪 带 Cookie 不能用 `*`        | `Allow-Credentials: true` 时必须指定具体 Origin |
| 🔀 CORS 不是认证               | 它是"许可"，不是"验证"，不要拿它当安全措施      |

> 💡 **最后一句话总结**：CORS 就是浏览器给后端出的一个"准入考题"——后端答对了（返回正确的响应头），浏览器就放行跨源请求；答错了，浏览器就拦截响应。**配置 CORS = 在后端写好答案。**