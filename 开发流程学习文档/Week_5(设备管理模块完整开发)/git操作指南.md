没问题！既然你是个人学习，那就用最实用的 **"main + feature" 两车道模式**。你之前所有代码直接怼到 `main` 的习惯要改一改，我下面手把手教你**从命令行到 GitHub 网页的完整操作**。

---

## 🛣️ 先理解"两车道"模式

| 分支                          | 比喻                   | 你的操作规范                               |
| ----------------------------- | ---------------------- | ------------------------------------------ |
| `main`                        | **高速公路（已通车）** | 只放**能跑通、能交作业**的完整代码         |
| `feature/week5-device-module` | **你的临时施工车道**   | 第五周作业全在这条分支上写，写废了随时能拆 |

**核心原则**：永远不直接在 `main` 上写代码。`main` 只负责"收成品"。

---

## 📝 完整操作流程（命令行 + GitHub 网页）

### 第一步：开工前，确保 main 是最新的

```bash
# 先回到 main 车道
git checkout main

# 把 GitHub 上最新的 main 拉下来（防冲突）
git pull origin main
```

---

### 第二步：开辟临时施工车道（feature 分支）

```bash
# 基于 main 创建并切换到 feature 分支
git checkout -b feature/week5-device-module
```

> 现在你开始写第五周的代码（CORS、DeviceController、校验注解…），全在这条分支上操作。

---

### 第三步：边写边做"原子提交"

比如你写完了 CORS 配置：

```bash
git add src/main/java/com/example/platform/config/CorsConfig.java
git commit -m "feat: 添加 CORS 跨域配置"
```

又比如写完了 Device 实体校验：

```bash
git add src/main/java/com/example/platform/entity/Device.java
git commit -m "feat: Device 实体添加校验注解"
```

> 所谓"原子提交"：一锤子只干一件事，方便以后回看或回滚。

---

### 第四步：把施工车道推到 GitHub 上

全部写完后，执行：

```bash
# 把本地 feature 分支第一次推送到 GitHub
git push origin feature/week5-device-module
```

> 推送成功后，GitHub 上就有了两条分支：`main`（老样子）和 `feature/week5-device-module`（你的新代码）。

---

### 第五步：在 GitHub 发起 Pull Request（重点！）

**Pull Request（PR）** 大白话就是：  
**"老板，我第五周的活干完了，申请把施工车道并到高速公路（main）上，请验收！"**

即使你是单人学习，这个步骤也很有用，因为 PR 页面能让你**最后检查一遍改了哪些文件**，避免把废代码、测试代码、密码 accidentally 推上去。

#### 具体操作：

**1. 打开你的 GitHub 仓库页面**  
推送后刷新网页，通常在代码列表**上方会看到一个黄色提示条**：

> `feature/week5-device-module` had recent pushes 2 minutes ago  
> **[Compare & pull request]** ← 点这个按钮

![示意图：GitHub 黄色提示条](https://docs.github.com/assets/cb-23923/images/help/pull_requests/recently-pushed-branch-banner.png)

> 如果黄色条消失了，或者你关闭了页面，没关系！点仓库顶部第三个 Tab **Pull requests** → 绿色按钮 **New pull request**。

---

**2. 进入"创建 PR"页面**

你会看到一个对比界面，确保两边选对了：

```
base: main  ←  compare: feature/week5-device-module
     ↑                    ↑
  目标分支（要合到哪里去）   源分支（你的新代码）
```

- **左边（base）**：选 `main`（高速公路）
- **右边（compare）**：选 `feature/week5-device-module`（施工车道）

页面下方会自动显示你这次改动的所有文件（绿色是新增，红色是删除）。

---

**3. 填写 PR 信息**

| 字段                    | 你写什么                                                     |
| ----------------------- | ------------------------------------------------------------ |
| **Title（标题）**       | `feat: 第五周设备管理模块完整实现`                           |
| **Description（描述）** | 可以写：<br>- 实现了 Device CRUD 接口<br>- 添加全局异常处理<br>- 配置 CORS 跨域<br>- Device 实体增加分组校验<br>- 修复第四周遗留注解问题 |

写完后点击 **Create pull request**。

---

**4. 审查自己的代码（这一步很多人跳过，但很重要）**

PR 创建成功后进入详情页，点第三个 Tab **Files changed**（文件变动）。

> 这里你能清楚看到每一行改了什么。比如检查下：
> - 有没有把数据库密码 commit 进去了？
> - 有没有把 `System.out.println` 调试语句忘删了？
> - `application.yml` 里的本地路径是不是不小心也推了？

如果有问题，**不需要关 PR**！在本地继续改，然后 `git add` → `git commit` → `git push origin feature/week5-device-module`，PR 页面会自动更新。

---

**5. 点击合并（Merge）**

确认代码没问题后，回到 PR 详情页第一个 Tab **Conversation**，拉到最下方，你会看到一个框：

```
Merge pull request
```

点击它旁边的下拉箭头，会出现三种合并方式：

| 方式                      | 白话解释                                                     | 推荐                                         |
| ------------------------- | ------------------------------------------------------------ | -------------------------------------------- |
| **Create a merge commit** | 保留你 feature 分支里每一次 commit 记录，main 上会完整看到你之前的 `feat: xxx`、`fix: xxx` | ✅ **个人学习推荐用这个**，能保留原子提交历史 |
| **Squash and merge**      | 把你 feature 上所有 commit 压扁成**一个大 commit**，再塞进 main。main 历史很干净，但丢失了细粒度记录 | 团队常用，个人不推荐                         |
| **Rebase and merge**      | 把你的 commit 接到 main 末尾，历史呈一条直线，没有"合并"的痕迹 | 进阶用法，暂时不用管                         |

**选择默认的 Create a merge commit**，然后点 **Confirm merge**。

页面会变成紫色：✅ **Pull request successfully merged and closed!**

---

**6. 删除远程 feature 分支（好习惯）**

合并成功后，同一个紫色框里会出现 **Delete branch** 按钮。  
点它删除 GitHub 上的 `feature/week5-device-module` 分支。

> 施工车道并到高速公路了，临时车道就可以拆了，留着占地方。

---

### 第六步：本地同步（很多人漏了这一步！）

合并是在 GitHub 网页上完成的，**你电脑本地的 `main` 分支此时还是旧的**！

```bash
# 回到 main
git checkout main

# 把 GitHub 上刚刚合并后的最新 main 拉下来
git pull origin main
```

此时你本地 `main` 已经有了第五周的全部代码。

---

### 第七步：清理本地 feature 分支

```bash
# 删除本地的 feature 分支（因为已经合完了）
git branch -d feature/week5-device-module
```

> 如果提示还没合并不让删，说明你忘了做第六步的 `git pull`，或者前面步骤漏了。

---

## 🔄 下第六周作业时，重复这套流程

```bash
git checkout main
git pull origin main                    # 先更新高速公路
git checkout -b feature/week6-xxx       # 再开新施工车道
# ... 写代码、add、commit ...
git push origin feature/week6-xxx
# ... 去 GitHub 发 PR、合并、删分支 ...
git checkout main && git pull origin main
git branch -d feature/week6-xxx
```

---

## 🎯 为什么要这么麻烦？直接 push main 不行吗？

直接 `git push origin main` 当然能跑，但养成 PR 习惯对个人学习有**隐藏收益**：

1. **期末复习时，PR 列表就是你的学习进度表**：第几周做了什么功能，点开 PR 一目了然。
2. **写简历有东西讲**：面试时展示 GitHub，面试官看到你有规范的 PR 流程、原子提交、分支管理，比"一团乱麻全在 main 上"专业得多。
3. **防止手残**：PR 页面的 `Files changed` 是你交作业前最后的安检门，能拦住 80% 的低级错误。
4. **为团队协作做准备**：现在学会这套，以后进公司第一天就能跟上节奏。

---

## ❗ 常见问题

**Q：PR 页面提示 "Can't automatically merge"（有冲突）怎么办？**  
A：说明你在写 feature 时，GitHub 上的 `main` 被更新了（比如你另一台电脑改了）。解决办法：
```bash
git checkout feature/week5-device-module
git pull origin main        # 把最新 main 拉进你的施工车道
# 手动解决冲突文件后
git add .
git commit -m "merge: 解决与 main 的冲突"
git push origin feature/week5-device-module
# PR 页面会自动恢复可合并状态
```

**Q：我点错了，PR 合并错了还能救吗？**  
A：可以！PR 页面合并后有个 **Revert** 按钮，能一键生成一个新的 PR 把代码回退掉。

**Q：自己一个人也要写 PR 描述那么详细吗？**  
A：个人学习可以简写，但**建议养成写的习惯**。哪怕只写一句话，也比空白强。

如果你第一次操作卡在哪一步，把截图或报错发给我，我帮你继续排雷！