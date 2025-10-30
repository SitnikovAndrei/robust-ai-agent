import os

def collect_files(root_dir, output_file):
    with open(output_file, 'w', encoding='utf-8') as out:
        for dirpath, _, filenames in os.walk(root_dir):
            for filename in filenames:
                file_path = os.path.join(dirpath, filename)
                # Разделитель с путем
                out.write(f"\n\n=== FILE: {file_path} ===\n")
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        out.write(f.read())
                except Exception as e:
                    out.write(f"\n[Ошибка чтения файла: {e}]\n")

if __name__ == "__main__":
    # Пример использования
    root_dir = "./src"      # папка, где искать файлы
    output_file = "all_files.txt"  # итоговый файл
    collect_files(root_dir, output_file)
    print(f"Все файлы объединены в {output_file}")
