from app.config import Settings


def test_ocr_defaults_match_contract() -> None:
    settings = Settings(_env_file=None)
    assert settings.ocr_max_image_bytes == 5 * 1024 * 1024
    assert settings.ocr_max_side == 1600
    assert settings.ocr_confidence_alert == 0.75
