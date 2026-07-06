import { Prop } from '../../gen/props-meta-golarion';
import { StateProp, StateTypes } from './state';

export type Expression<T> = 
     T extends boolean ? BooleanExpression
    :T extends number ? NumberExpression
    :never;

type NumberExpression =
    Get<number>
    |GlobalState<TypeFilter<StateTypes, number>>
    |['zoom']
    |['+', NumberExpression, NumberExpression]
    |['number', NumberExpression]
    |number;


type BooleanExpression =
    Equals<any>
    |Get<boolean>
    |GlobalState<TypeFilter<StateTypes, boolean>>
    |['has', Prop]
    |['!', BooleanExpression]
    |['all', ...BooleanExpression[]]
    |['any', ...BooleanExpression[]]
    |['>=', Expression<number>, Expression<number>]
    |['>', Expression<number>, Expression<number>]
    |['<=', Expression<number>, Expression<number>]
    |['<', Expression<number>, Expression<number>]
    |['boolean', BooleanExpression]
    |boolean;

type Equals<T> = ['==', Expression<T>, Expression<T>];
type Get<T> = ['get', Prop];
export type GlobalState<K extends StateProp> = ['global-state', K];


type TypeFilter<Types, T> = {
    [K in keyof Types]: Types[K] extends T ? K : never
}[keyof Types];
