GRADLE := ./gradlew
AVD ?= Pixel_10
BACKEND_PORT ?= 8088

.DEFAULT_GOAL := release

.PHONY: help release build install test fmt check clean profile server server-build server-test server-image emulator

help: ## 列出所有可用命令
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  make \033[36m%-9s\033[0m %s\n", $$1, $$2}'

release: ## 编译 release APK（默认目标：直接 make 即构建）
	$(GRADLE) :app:assembleRelease

build: ## 编译 debug APK
	$(GRADLE) :app:assembleDebug

install: ## 编译并安装到设备/模拟器
	$(GRADLE) :app:installDebug

emulator: ## 启动/复用硬件加速模拟器，并连接本机朋友圈后端
	@AVD="$(AVD)" BACKEND_PORT="$(BACKEND_PORT)" ./scripts/emulator.sh

server: ## 启动本地朋友圈后端（自动加载 server/.env）
	@cd server && \
		set -a && \
		if [ -f .env ]; then . ./.env; fi && \
		: "$${DATABASE_URL:?请在 server/.env 或环境变量中设置 DATABASE_URL}" && \
		exec cargo run --locked

server-build: ## 构建带版本与 Git hash 的后端 Release 二进制
	cargo build --manifest-path server/Cargo.toml --release --locked

server-test: ## 后端单元测试与一次性 PostgreSQL/HTTP 集成测试
	cargo test --manifest-path server/Cargo.toml --locked
	python3 -m unittest discover -s scripts/tests -v
	bash server/tests/run.sh

server-image: ## 构建带当前 Git hash 的本地后端 Docker 镜像
	docker build --build-arg YAYA_GIT_SHA="$$(git rev-parse HEAD)" -t yayacal-moments-api:local server

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
