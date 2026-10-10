.PHONY: check check-all check-tend check-tend-release check-tend-migrations \
	check-tend-multiship check-mobile \
	dist-tend test test-js \
	validate-plugin lint-qml lint-python

OMARCHY_ROOT ?= $(if $(OMARCHY_PATH),$(OMARCHY_PATH),$(HOME)/.local/share/omarchy)

check: check-tend

check-all: check-tend check-mobile

check-tend: validate-plugin lint-qml lint-python test

check-mobile:
	cd mobile && ./gradlew :shared:jvmTest :androidApp:lintDebug :androidApp:assembleDebug

check-tend-release:
	scripts/check-tend-release.sh

check-tend-migrations:
	@test -n "$(TEND_TEST_PIER)" || { echo "set TEND_TEST_PIER to a running fake-ship pier" >&2; exit 64; }
	scripts/check-tend-migrations.sh "$(TEND_TEST_PIER)"

check-tend-multiship:
	@test -n "$(TEND_OWNER_CONFIG)" || { echo "set TEND_OWNER_CONFIG" >&2; exit 64; }
	@test -n "$(TEND_OWNER_COOKIE)" || { echo "set TEND_OWNER_COOKIE" >&2; exit 64; }
	@test -n "$(TEND_OWNER_PIER)" || { echo "set TEND_OWNER_PIER" >&2; exit 64; }
	@test -n "$(TEND_MEMBER_A_CONFIG)" || { echo "set TEND_MEMBER_A_CONFIG" >&2; exit 64; }
	@test -n "$(TEND_MEMBER_A_COOKIE)" || { echo "set TEND_MEMBER_A_COOKIE" >&2; exit 64; }
	@test -n "$(TEND_MEMBER_A_PIER)" || { echo "set TEND_MEMBER_A_PIER" >&2; exit 64; }
	@test -n "$(TEND_MEMBER_B_CONFIG)" || { echo "set TEND_MEMBER_B_CONFIG" >&2; exit 64; }
	@test -n "$(TEND_MEMBER_B_COOKIE)" || { echo "set TEND_MEMBER_B_COOKIE" >&2; exit 64; }
	@test -n "$(TEND_MEMBER_B_PIER)" || { echo "set TEND_MEMBER_B_PIER" >&2; exit 64; }
	scripts/check-tend-multiship.py \
		--owner-config "$(TEND_OWNER_CONFIG)" --owner-cookie "$(TEND_OWNER_COOKIE)" --owner-pier "$(TEND_OWNER_PIER)" \
		--member-a-config "$(TEND_MEMBER_A_CONFIG)" --member-a-cookie "$(TEND_MEMBER_A_COOKIE)" --member-a-pier "$(TEND_MEMBER_A_PIER)" \
		--member-b-config "$(TEND_MEMBER_B_CONFIG)" --member-b-cookie "$(TEND_MEMBER_B_COOKIE)" --member-b-pier "$(TEND_MEMBER_B_PIER)" \
		$(TEND_MULTISHIP_OPTIONS)

dist-tend:
	scripts/build-tend-release.sh

validate-plugin:
	omarchy plugin validate omarchy-plugin

lint-qml:
	qmllint -I "$(OMARCHY_ROOT)/shell" \
		omarchy-plugin/TendPanel.qml \
		omarchy-plugin/BarWidget.qml \
		omarchy-plugin/ReminderDetail.qml \
		omarchy-plugin/TendAssigneePicker.qml \
		omarchy-plugin/TendDateTimePicker.qml \
		omarchy-plugin/TendEditMenu.qml \
		omarchy-plugin/TendFlagButton.qml \
		omarchy-plugin/TendOpenGlyph.qml \
		omarchy-plugin/TendMenuItem.qml \
	omarchy-plugin/TendSettings.qml \
	omarchy-plugin/TendTextField.qml \
	omarchy-plugin/ReminderDefaults.qml \
		omarchy-plugin/TendButton.qml \
		omarchy-plugin/TendCheckbox.qml \
		omarchy-plugin/TendCheck.qml \
		omarchy-plugin/TendDropdown.qml \
		omarchy-plugin/TendNumber.qml \
		omarchy-plugin/TendTextArea.qml \
		omarchy-plugin/Service.qml

lint-python:
	python3 -m py_compile omarchy-plugin/transport/eyre_client.py
	python3 -m py_compile bin/tend
	python3 -m py_compile scripts/check-tend-multiship.py

test: test-js
	python3 -m unittest discover -s tests -p 'test_*.py'

test-js:
	node --test tests/test_tend_*.js
