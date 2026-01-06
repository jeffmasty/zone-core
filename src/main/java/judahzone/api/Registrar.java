package judahzone.api;

import judahzone.api.FX.Calc;

public interface Registrar {
	void registerAnalyzer(Calc<?> analyzer);
	void unregisterAnalyzer(Calc<?> analyzer);
}
