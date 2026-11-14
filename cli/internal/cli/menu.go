package cli

import (
	"fmt"
	"strings"

	"undercover/internal/client"
	"undercover/internal/engine"
	"undercover/internal/share"
	"undercover/internal/state"
	"undercover/internal/ui"
)

// RunInteractiveMenu is launched when 'uc' is invoked without subcommands
func RunInteractiveMenu() error {
	for {
		ui.ClearScreen()
		choice, err := ui.RunOnboardingMenu(Version)
		if err != nil || choice == "exit" || choice == "" {
			fmt.Println("Goodbye.")
			return nil
		}

		ui.ClearScreen()

		if choice == "manager" {
			runServerManagerMenu()
		} else if choice == "client" {
			runClientOperationsMenu()
		}

		// Prompt to return or exit
		retChoice, _ := ui.RunCustomSelector("MAIN MENU", "Choose next step", []ui.SelectorOption{
			{ID: "menu", Number: "1", Title: "Вернуться на главный экран", Description: "Открыть селектор режимов Onboarding"},
			{ID: "exit", Number: "2", Title: "Выход", Description: "Завершить работу с CLI"},
		})

		if retChoice != "menu" {
			fmt.Println("Goodbye.")
			return nil
		}
	}
}

// Server Manager Menu
func runServerManagerMenu() {
	for {
		ui.ClearScreen()
		options := []ui.SelectorOption{
			{ID: "deploy", Number: "1", Title: "Развернуть сервер (Deploy)", Description: "Установка sing-box VLESS+Reality или Hysteria 2 на VPS"},
			{ID: "users", Number: "2", Title: "Управление клиентами (Users)", Description: "Выпуск профилей доступа, список, QR-коды и ссылки"},
			{ID: "doctor", Number: "3", Title: "Диагностика узла (Doctor)", Description: "Проверка здоровья портов, time sync и маскировки"},
			{ID: "status", Number: "4", Title: "Статус службы (Status)", Description: "Аптайм сервиса, версия ядра, число клиентов"},
			{ID: "logs", Number: "5", Title: "Журнал логов (Logs)", Description: "Просмотр последних записей journalctl"},
			{ID: "export", Number: "6", Title: "Экспорт профилей (Export)", Description: "Выгрузка ключей и ссылок в JSON/файл"},
			{ID: "update", Number: "7", Title: "Обновление ядра (Update)", Description: "Обновление sing-box с безопасным откатом"},
			{ID: "uninstall", Number: "8", Title: "Удаление (Uninstall)", Description: "Полный демонтаж сервиса и правил firewall"},
			{ID: "back", Number: "9", Title: "Назад", Description: "Вернуться на главный экран"},
		}

		choice, err := ui.RunCustomSelector("РЕЖИМ МЕНЕДЖЕРА // SERVER OPS", "Администрирование серверного узла", options)
		if err != nil || choice == "back" || choice == "" {
			return
		}

		ui.ClearScreen()

		switch choice {
		case "deploy":
			_ = deployCmd.RunE(deployCmd, []string{})
		case "users":
			runInteractiveUsersMenu()
		case "doctor":
			_ = doctorCmd.RunE(doctorCmd, []string{})
		case "status":
			_ = statusCmd.RunE(statusCmd, []string{})
		case "logs":
			_ = logsCmd.RunE(logsCmd, []string{})
		case "export":
			_ = exportCmd.RunE(exportCmd, []string{})
		case "update":
			_ = updateCmd.RunE(updateCmd, []string{})
		case "uninstall":
			_ = uninstallCmd.RunE(uninstallCmd, []string{})
		}

		fmt.Println()
		next, _ := ui.RunCustomSelector("SERVER OPS", "Продолжить работу в режиме менеджера?", []ui.SelectorOption{
			{ID: "stay", Number: "1", Title: "Остаться в меню менеджера", Description: "Выбрать другое действие с сервером"},
			{ID: "back", Number: "2", Title: "Назад", Description: "Вернуться в главное меню"},
		})
		if next != "stay" {
			return
		}
	}
}

// Client Operations Menu
func runClientOperationsMenu() {
	for {
		ui.ClearScreen()
		cStatus := client.GetClientStatus()
		store, _ := state.LoadLocalStore()
		hasProfiles := store != nil && len(store.Servers) > 0

		var options []ui.SelectorOption
		var statusSub string

		if !hasProfiles {
			statusSub = "Профили: НЕТ ИМПОРТИРОВАННЫХ ССЫЛОК"
			options = []ui.SelectorOption{
				{
					ID:          "import",
					Number:      "1",
					Title:       "Импортировать ссылку (Import)",
					Description: "Вставьте vless:// или hysteria2:// для настройки VPN",
				},
				{
					ID:          "back",
					Number:      "2",
					Title:       "Назад",
					Description: "Вернуться на главный экран",
				},
			}
		} else {
			if !cStatus.Connected {
				statusSub = fmt.Sprintf("Туннель: ОТКЛЮЧЕН • Сохранено профилей: %d", len(store.Servers))
				options = append(options, ui.SelectorOption{
					ID:          "connect",
					Number:      "1",
					Title:       "Подключиться к VPN (Connect)",
					Description: "Запуск локального туннеля sing-box и настройка системного прокси",
				})
			} else {
				statusSub = fmt.Sprintf("Туннель: АКТИВЕН (127.0.0.1:%d, PID %d)", client.DefaultLocalProxyPort, cStatus.PID)
				options = append(options, ui.SelectorOption{
					ID:          "disconnect",
					Number:      "1",
					Title:       "Отключиться от VPN (Disconnect)",
					Description: "Остановка локального туннеля и сброс системного прокси",
				})
			}

			options = append(options,
				ui.SelectorOption{ID: "status", Number: "2", Title: "Статус подключения (Status)", Description: "Проверка активности локального прокси и PID"},
				ui.SelectorOption{ID: "profiles", Number: "3", Title: fmt.Sprintf("Управление профилями (%d)", len(store.Servers)), Description: "Просмотр, переключение активного узла или удаление"},
				ui.SelectorOption{ID: "import", Number: "4", Title: "Импорт ссылки (Import)", Description: "Добавить еще одну vless:// или hysteria2:// ссылку"},
				ui.SelectorOption{ID: "back", Number: "5", Title: "Назад", Description: "Вернуться на главный экран"},
			)
		}

		choice, err := ui.RunCustomSelector("РЕЖИМ КЛИЕНТА // WORKSTATION TUNNEL", statusSub, options)
		if err != nil || choice == "back" || choice == "" {
			return
		}

		ui.ClearScreen()

		switch choice {
		case "connect":
			selectAndConnectServer(store)
		case "disconnect":
			_ = disconnectCmd.RunE(disconnectCmd, []string{})
		case "status":
			st := client.GetClientStatus()
			if st.Connected {
				ui.Success("VPN туннель АКТИВЕН (PID %d)", st.PID)
				fmt.Printf("  Локальный прокси: %s (SOCKS5/HTTP)\n", st.LocalProxyAddr)
				fmt.Println(ui.MutedStyle.Render("  Для консоли: export all_proxy=socks5://127.0.0.1:2080"))
			} else {
				ui.Warning("VPN туннель ОТКЛЮЧЕН")
			}
		case "profiles":
			runProfilesMenu(store)
		case "import":
			promptImportLink()
		}

		fmt.Println()
		next, _ := ui.RunCustomSelector("CLIENT OPS", "Продолжить работу в режиме клиента?", []ui.SelectorOption{
			{ID: "stay", Number: "1", Title: "Остаться в меню клиента", Description: "Управление туннелем рабочей станции"},
			{ID: "back", Number: "2", Title: "Назад", Description: "Вернуться в главное меню"},
		})
		if next != "stay" {
			return
		}
	}
}

func promptImportLink() bool {
	var linkInput string
	fmt.Print("Вставьте vless:// или hysteria2:// ссылку: ")
	_, _ = fmt.Scanln(&linkInput)
	trimmed := strings.TrimSpace(linkInput)
	if trimmed == "" {
		return false
	}

	parsed, err := share.ParseShareLink(trimmed)
	if err != nil {
		ui.Error("Ошибка импорта", err.Error(), "Ссылка должна начинаться с vless:// или hysteria2://")
		fmt.Println("Нажмите Enter для продолжения...")
		_, _ = fmt.Scanln()
		return false
	}

	store, err := state.LoadLocalStore()
	if err != nil {
		ui.Error("Ошибка", err.Error(), "")
		return false
	}

	alias := parsed.Alias
	if alias == "" {
		alias = fmt.Sprintf("Undercover-%s", parsed.Host)
	}

	srv := state.LocalServer{
		Host:          parsed.Host,
		VPNPort:       parsed.Port,
		Protocol:      parsed.Protocol,
		ServerName:    parsed.ServerName,
		PublicKey:     parsed.PublicKey,
		ShortID:       parsed.ShortID,
		CertPinSha256: parsed.PinSHA256,
		UserAuth:      parsed.Auth,
		ShareLink:     trimmed,
		Alias:         alias,
	}

	store.AddOrUpdateServer(srv)
	if err := state.SaveLocalStore(store); err != nil {
		ui.Error("Ошибка сохранения", err.Error(), "")
		return false
	}

	fmt.Println()
	ui.Success("Профиль '%s' (%s:%d, %s) успешно сохранен!", srv.Alias, srv.Host, srv.VPNPort, srv.Protocol)
	fmt.Println()

	connectNow, err := ui.ConfirmAction("Подключение", "Подключиться к VPN прямо сейчас?", true)
	if err == nil && connectNow {
		_ = connectCmd.RunE(connectCmd, []string{srv.Alias})
	}
	return true
}

func selectAndConnectServer(store *state.LocalStore) {
	if len(store.Servers) == 0 {
		return
	}
	if len(store.Servers) == 1 {
		_ = connectCmd.RunE(connectCmd, []string{store.Servers[0].Alias})
		return
	}

	opts := make([]ui.SelectorOption, 0, len(store.Servers)+1)
	for i, s := range store.Servers {
		proto := "VLESS+Reality"
		if s.Protocol == "hysteria2" {
			proto = "Hysteria 2"
		}
		opts = append(opts, ui.SelectorOption{
			ID:          s.Alias,
			Number:      fmt.Sprintf("%d", i+1),
			Title:       s.Alias,
			Description: fmt.Sprintf("%s:%d • %s", s.Host, s.VPNPort, proto),
		})
	}
	opts = append(opts, ui.SelectorOption{
		ID:          "back",
		Number:      fmt.Sprintf("%d", len(opts)+1),
		Title:       "Назад",
		Description: "Отмена подключения",
	})

	choice, err := ui.RunCustomSelector("ВЫБОР СЕРВЕРА // PROFILES", "Выберите узел для подключения", opts)
	if err != nil || choice == "back" || choice == "" {
		return
	}
	_ = connectCmd.RunE(connectCmd, []string{choice})
}

func runProfilesMenu(store *state.LocalStore) {
	for {
		ui.ClearScreen()
		if len(store.Servers) == 0 {
			ui.Warning("Нет сохраненных профилей")
			return
		}

		opts := make([]ui.SelectorOption, 0, len(store.Servers)+1)
		for i, s := range store.Servers {
			proto := "VLESS+Reality"
			if s.Protocol == "hysteria2" {
				proto = "Hysteria 2"
			}
			opts = append(opts, ui.SelectorOption{
				ID:          s.Alias,
				Number:      fmt.Sprintf("%d", i+1),
				Title:       s.Alias,
				Description: fmt.Sprintf("%s:%d • %s", s.Host, s.VPNPort, proto),
			})
		}
		opts = append(opts, ui.SelectorOption{
			ID:          "back",
			Number:      fmt.Sprintf("%d", len(opts)+1),
			Title:       "Назад",
			Description: "Вернуться в меню клиента",
		})

		choice, err := ui.RunCustomSelector("УПРАВЛЕНИЕ ПРОФИЛЯМИ // SAVED NODES", "Выберите профиль для действий", opts)
		if err != nil || choice == "back" || choice == "" {
			return
		}

		selectedSrv := store.FindServer(choice)
		if selectedSrv == nil {
			return
		}

		ui.ClearScreen()
		actionOpts := []ui.SelectorOption{
			{ID: "connect", Number: "1", Title: "Подключиться к этому узлу", Description: fmt.Sprintf("Установить туннель к %s", selectedSrv.Host)},
			{ID: "delete", Number: "2", Title: "Удалить профиль", Description: "Удалить этот профиль из списка сохраненных"},
			{ID: "back", Number: "3", Title: "Назад", Description: "Вернуться к списку узлов"},
		}

		header := fmt.Sprintf("УЗЕЛ: %s (%s:%d)", selectedSrv.Alias, selectedSrv.Host, selectedSrv.VPNPort)
		act, _ := ui.RunCustomSelector(header, "Действие с профилем", actionOpts)
		if act == "connect" {
			_ = connectCmd.RunE(connectCmd, []string{selectedSrv.Alias})
			return
		} else if act == "delete" {
			confirmed, _ := ui.ConfirmAction("Удаление профиля", fmt.Sprintf("Удалить профиль '%s'?", selectedSrv.Alias), false)
			if confirmed {
				store.RemoveServer(selectedSrv.Alias)
				_ = state.SaveLocalStore(store)
				ui.Success("Профиль удален.")
			}
		}
	}
}

func runInteractiveUsersMenu() {
	ui.ClearScreen()
	options := []ui.SelectorOption{
		{ID: "list", Number: "1", Title: "Список клиентов (List)", Description: "Просмотр всех выпущенных профилей доступа"},
		{ID: "add", Number: "2", Title: "Добавить клиента (Add)", Description: "Генерация нового UUID и получение share-ссылки"},
		{ID: "show", Number: "3", Title: "Показать QR-код (Show)", Description: "Отобразить QR-код и vless:// ссылку для пользователя"},
		{ID: "remove", Number: "4", Title: "Удалить клиента (Remove)", Description: "Отозвать доступ пользователя с перезапуском сервиса"},
		{ID: "back", Number: "5", Title: "Назад", Description: "Вернуться в меню менеджера"},
	}

	userChoice, err := ui.RunCustomSelector("УПРАВЛЕНИЕ КЛИЕНТАМИ // IDENTITIES", "Выпуск и отзыв профилей", options)
	if err != nil || userChoice == "back" || userChoice == "" {
		return
	}

	target, host, err := connectTarget()
	if err != nil {
		ui.Error("Connection Failed", err.Error(), "")
		return
	}
	defer target.Close()

	switch userChoice {
	case "list":
		st, err := engine.LoadServerState(target)
		if err != nil {
			ui.Error("Error", err.Error(), "")
			return
		}
		fmt.Printf("\nВыпущенные профили (%d всего):\n\n", len(st.Users))
		fmt.Printf("  %-20s %-38s %s\n", "NAME", "CREDENTIAL", "CREATED AT")
		fmt.Println("  ----------------------------------------------------------------------------")
		for _, u := range st.Users {
			cred := u.UUID
			if cred == "" {
				cred = u.Password
			}
			fmt.Printf("  %-20s %-38s %s\n", u.Name, cred, u.CreatedAt.Format("2006-01-02 15:04:05"))
		}
		fmt.Println()

	case "add":
		var userName string
		fmt.Print("Имя нового профиля (например: laptop, iphone, alice): ")
		_, _ = fmt.Scanln(&userName)
		if strings.TrimSpace(userName) == "" {
			return
		}
		res, err := engine.UserAdd(target, host, strings.TrimSpace(userName))
		if err != nil {
			ui.Error("Failed to add user", err.Error(), "")
			return
		}
		ui.Success("Профиль '%s' успешно создан!", userName)
		if res.QRCode != "" {
			fmt.Println(res.QRCode)
		}
		fmt.Println("Share Link:")
		fmt.Println(ui.SuccessStyle.Render(res.ShareLink))

	case "show":
		st, err := engine.LoadServerState(target)
		if err != nil || len(st.Users) == 0 {
			ui.Error("No users", "На сервере нет зарегистрированных пользователей", "")
			return
		}
		userOpts := make([]ui.SelectorOption, 0, len(st.Users))
		for i, u := range st.Users {
			cred := u.UUID
			if cred == "" {
				cred = u.Password
			}
			userOpts = append(userOpts, ui.SelectorOption{
				ID:          u.Name,
				Number:      fmt.Sprintf("%d", i+1),
				Title:       u.Name,
				Description: fmt.Sprintf("ID: %s", cred),
			})
		}
		selected, err := ui.RunCustomSelector("ВЫБОР КЛИЕНТА", "Выберите профиль для отображения QR-кода", userOpts)
		if err != nil || selected == "" || selected == "back" {
			return
		}
		res, err := engine.UserShow(target, host, selected)
		if err != nil {
			ui.Error("Error", err.Error(), "")
			return
		}
		if res.QRCode != "" {
			fmt.Println(res.QRCode)
		}
		fmt.Println("Share Link:")
		fmt.Println(ui.SuccessStyle.Render(res.ShareLink))

	case "remove":
		st, err := engine.LoadServerState(target)
		if err != nil || len(st.Users) == 0 {
			ui.Error("No users", "На сервере нет пользователей для удаления", "")
			return
		}
		userOpts := make([]ui.SelectorOption, 0, len(st.Users))
		for i, u := range st.Users {
			cred := u.UUID
			if cred == "" {
				cred = u.Password
			}
			userOpts = append(userOpts, ui.SelectorOption{
				ID:          u.Name,
				Number:      fmt.Sprintf("%d", i+1),
				Title:       u.Name,
				Description: fmt.Sprintf("ID: %s", cred),
			})
		}
		selected, err := ui.RunCustomSelector("УДАЛЕНИЕ КЛИЕНТА", "Выберите профиль для отзыва доступа", userOpts)
		if err != nil || selected == "" || selected == "back" {
			return
		}

		confirmed, err := ui.ConfirmAction("Подтверждение", fmt.Sprintf("Отозвать доступ для '%s'?", selected), false)
		if err != nil || !confirmed {
			fmt.Println("Отменено.")
			return
		}
		if err := engine.UserRemove(target, selected); err != nil {
			ui.Error("Removal failed", err.Error(), "")
			return
		}
		ui.Success("Пользователь '%s' удален.", selected)
	}
}
