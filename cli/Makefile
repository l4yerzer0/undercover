GO ?= go
GOTOOLCHAIN ?= local
BIN_DIR ?= bin

.PHONY: all build build-linux build-linux-arm64 build-windows build-windows-arm64 build-all test clean install

all: build

build:
	@mkdir -p $(BIN_DIR)
	GOTOOLCHAIN=$(GOTOOLCHAIN) $(GO) build -ldflags "-s -w" -o $(BIN_DIR)/uc ./cmd/uc

build-linux:
	@mkdir -p $(BIN_DIR)
	GOTOOLCHAIN=$(GOTOOLCHAIN) CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GO) build -ldflags "-s -w" -o $(BIN_DIR)/uc-linux-amd64 ./cmd/uc
	@echo "Built $(BIN_DIR)/uc-linux-amd64"

build-linux-arm64:
	@mkdir -p $(BIN_DIR)
	GOTOOLCHAIN=$(GOTOOLCHAIN) CGO_ENABLED=0 GOOS=linux GOARCH=arm64 $(GO) build -ldflags "-s -w" -o $(BIN_DIR)/uc-linux-arm64 ./cmd/uc
	@echo "Built $(BIN_DIR)/uc-linux-arm64"

build-windows:
	@mkdir -p $(BIN_DIR)
	GOTOOLCHAIN=$(GOTOOLCHAIN) CGO_ENABLED=0 GOOS=windows GOARCH=amd64 $(GO) build -ldflags "-s -w" -o $(BIN_DIR)/uc-windows-amd64.exe ./cmd/uc
	@echo "Built $(BIN_DIR)/uc-windows-amd64.exe"

build-windows-arm64:
	@mkdir -p $(BIN_DIR)
	GOTOOLCHAIN=$(GOTOOLCHAIN) CGO_ENABLED=0 GOOS=windows GOARCH=arm64 $(GO) build -ldflags "-s -w" -o $(BIN_DIR)/uc-windows-arm64.exe ./cmd/uc
	@echo "Built $(BIN_DIR)/uc-windows-arm64.exe"

build-all: build-linux build-linux-arm64 build-windows build-windows-arm64

test:
	GOTOOLCHAIN=$(GOTOOLCHAIN) $(GO) test -v ./...

install: build
	@mkdir -p $(HOME)/.local/bin
	install -m 0755 $(BIN_DIR)/uc $(HOME)/.local/bin/uc
	@echo "Installed to $(HOME)/.local/bin/uc"

clean:
	rm -rf $(BIN_DIR)
