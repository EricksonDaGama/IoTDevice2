.PHONY: clean

clean:
	@echo "Limpando arquivos .cert em /output/server/certificado..."
	@find ./output/server/certificado -type f -name '*.cert' -exec rm {} +

	@echo "Limpando arquivos .jpeg em /output/server/img..."
	@find ./output/server/img -type f -name '*.jpeg' -exec rm {} +

	@echo "Limpando conteúdo de domain.txt, device.txt e user.txt..."
	@printf "" > ./output/server/domain.txt
	@printf "" > ./output/server/device.txt
	@printf "" > ./output/server/user.txt
