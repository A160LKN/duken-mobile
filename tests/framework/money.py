def tenge(tiyn: int) -> str:
    """Сумма так, как её показывает приложение: 18999000 → «189 990 ₸» (неразрывные пробелы)."""
    whole, rest = divmod(tiyn, 100)
    grouped = f"{whole:,}".replace(",", " ")
    return f"{grouped} ₸" if rest == 0 else f"{grouped},{rest:02d} ₸"
