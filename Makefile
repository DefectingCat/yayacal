GRADLE := ./gradlew

.DEFAULT_GOAL := release

.PHONY: help release build install test fmt check clean profile

help: ## 列出所有可用命令
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  make \033[36m%-9s\033[0m %s\n", $$1, $$2}'

release: ## 编译 release APK（默认目标：直接 make 即构建）
	$(GRADLE) :app:assembleRelease

build: ## 编译 debug APK
	$(GRADLE) :app:assembleDebug

install: ## 编译并安装到设备/模拟器
	$(GRADLE) :app:installDebug

test: ## 跑 :core 单元测试（make test T=CalendarUtilsTest 只跑单个类）
ifdef T
	$(GRADLE) :core:testDebugUnitTest --tests "*$(T)"
else
	$(GRADLE) :core:testDebugUnitTest
endif

fmt: ## spotlessApply 格式化（提交前必跑）
	$(GRADLE) spotlessApply

check: ## 提交前一把梭：格式化 + 单测 + 装真机
	$(GRADLE) spotlessApply :core:testDebugUnitTest :app:installDebug

clean: ## 清理构建产物
	$(GRADLE) clean

profile: ## 抓 Perfetto trace（透传参数：make profile ARGS="--list-scenarios"）
	./scripts/profile.sh $(ARGS)
